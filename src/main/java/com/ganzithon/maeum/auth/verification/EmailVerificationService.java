package com.ganzithon.maeum.auth.verification;

import com.ganzithon.maeum.auth.domain.EmailAddress;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class EmailVerificationService {
    private static final String EMAIL = "EMAIL_VERIFICATION";
    private static final String SIGNUP = "SIGNUP_VERIFICATION";
    private final JdbcTemplate jdbc;
    private final VerificationTokens tokens;
    private final VerificationMailSender sender;
    private final Validator validator;
    private final Clock clock;
    private final int ipMaxPerMinute;

    public EmailVerificationService(JdbcTemplate jdbc, VerificationTokens tokens,
            VerificationMailSender sender, Validator validator, Clock clock,
            @Value("${app.verification.ip-max-per-minute:30}") int ipMaxPerMinute) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.sender = sender;
        this.validator = validator;
        this.clock = clock;
        if (ipMaxPerMinute < 1) throw new IllegalArgumentException("Invalid rate limit");
        this.ipMaxPerMinute = ipMaxPerMinute;
    }

    @Transactional(timeout = 20)
    public void send(String rawEmail, String clientIp) {
        String email = normalizeEmail(rawEmail);
        Instant now = clock.instant();
        // IP → 이메일 순서로 잠급니다. 전달 헤더가 아닌 서버에서 확인한 원격 주소를 사용합니다.
        Limit ip = lockLimit("ip:" + clientIp, now);
        Limit mailbox = lockLimit("email:" + email, now);
        if (mailbox.lastSent() != null && now.isBefore(mailbox.lastSent().plusSeconds(60))) {
            throw rateLimited();
        }
        boolean sameWindow = now.isBefore(ip.windowStarted().plusSeconds(60));
        int count = sameWindow ? ip.count() : 0;
        if (count >= ipMaxPerMinute) throw rateLimited();
        String token = tokens.issue();
        // 시연 규모의 동기 SMTP 전송입니다. 실패 시 DB 변경은 롤백되어 이전 링크를 보존합니다.
        sender.send(email, token);
        invalidate(email, EMAIL, now);
        saveToken(email, EMAIL, token, now.plusSeconds(1800), null, now);
        jdbc.update("UPDATE email_verification_limits SET last_sent_at=? WHERE limit_key=?",
                ts(now), tokens.hash("email:" + email));
        jdbc.update("UPDATE email_verification_limits SET window_started_at=?, request_count=? WHERE limit_key=?",
                ts(sameWindow ? ip.windowStarted() : now), count + 1, tokens.hash("ip:" + clientIp));
    }

    @Transactional
    public Confirmation confirm(String token) {
        Verification initial = find(token);
        lockLimit("email:" + initial.email(), clock.instant());
        Verification current = findLocked(token);
        Instant now = clock.instant();
        validate(current, EMAIL, now);
        jdbc.update("UPDATE auth_verifications SET used_at=? WHERE token_hash=?", ts(now), tokens.hash(token));
        invalidate(current.email(), SIGNUP, now);
        String proof = tokens.issue();
        saveToken(current.email(), SIGNUP, proof, now.plusSeconds(600), now, now);
        return new Confirmation(current.email(), proof, 600);
    }

    /** 후속 회원가입 서비스의 트랜잭션 안에서만 호출합니다. 가입 실패 시 소비도 롤백됩니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant consumeSignupProof(String token, String rawEmail) {
        Verification initial = find(token);
        lockLimit("email:" + initial.email(), clock.instant());
        Verification proof = findLocked(token);
        Instant now = clock.instant();
        validate(proof, SIGNUP, now);
        if (!proof.email().equals(normalizeEmail(rawEmail))) throw invalid();
        jdbc.update("UPDATE auth_verifications SET used_at=? WHERE token_hash=?", ts(now), tokens.hash(token));
        return proof.verifiedAt();
    }

    private String normalizeEmail(String raw) {
        EmailAddress email = new EmailAddress(raw);
        if (!validator.validate(email).isEmpty()) throw new IllegalArgumentException("Invalid email");
        return email.value();
    }

    private Limit lockLimit(String key, Instant now) {
        String hash = tokens.hash(key);
        jdbc.update("INSERT INTO email_verification_limits (limit_key, window_started_at, request_count) VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE limit_key=limit_key",
                hash, ts(now));
        return jdbc.queryForObject("SELECT * FROM email_verification_limits WHERE limit_key=? FOR UPDATE",
                (rs, row) -> new Limit(readInstant(rs, "window_started_at"),
                        rs.getInt("request_count"), readInstant(rs, "last_sent_at")), hash);
    }

    private Verification find(String token) { return lookup(token, false); }
    private Verification findLocked(String token) { return lookup(token, true); }

    private Verification lookup(String token, boolean lock) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        List<Verification> result = jdbc.query("SELECT * FROM auth_verifications WHERE token_hash=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Verification(rs.getString("email"), rs.getString("purpose"),
                        readInstant(rs, "expires_at"), readInstant(rs, "verified_at"),
                        readInstant(rs, "used_at"), readInstant(rs, "invalidated_at")), tokens.hash(token));
        if (result.isEmpty()) throw invalid();
        return result.getFirst();
    }

    private void validate(Verification token, String purpose, Instant now) {
        if (!token.purpose().equals(purpose)) throw invalid();
        if (token.usedAt() != null || token.invalidatedAt() != null) {
            throw new VerificationException(HttpStatus.CONFLICT, "AUTH_EMAIL_VERIFICATION_USED", "이미 사용했거나 무효화된 인증입니다.");
        }
        if (!now.isBefore(token.expiresAt())) {
            throw new VerificationException(HttpStatus.GONE, "AUTH_EMAIL_VERIFICATION_EXPIRED", "인증 유효시간이 지났습니다. 다시 인증해주세요.");
        }
    }

    private void invalidate(String email, String purpose, Instant now) {
        jdbc.update("UPDATE auth_verifications SET invalidated_at=? WHERE email=? AND purpose=? AND used_at IS NULL AND invalidated_at IS NULL",
                ts(now), email, purpose);
    }

    private void saveToken(String email, String purpose, String token, Instant expiry, Instant verifiedAt, Instant now) {
        jdbc.update("INSERT INTO auth_verifications (email,purpose,token_hash,expires_at,verified_at,created_at) VALUES (?,?,?,?,?,?)",
                email, purpose, tokens.hash(token), ts(expiry), ts(verifiedAt), ts(now));
    }

    private VerificationException invalid() {
        return new VerificationException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_VERIFICATION_INVALID", "올바르지 않은 인증입니다.");
    }
    private VerificationException rateLimited() {
        return new VerificationException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", "잠시 후 인증 메일을 다시 요청해주세요.");
    }
    private static LocalDateTime ts(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant readInstant(java.sql.ResultSet result, String name) throws java.sql.SQLException {
        LocalDateTime value = result.getObject(name, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
    private record Limit(Instant windowStarted, int count, Instant lastSent) { }
    private record Verification(String email, String purpose, Instant expiresAt, Instant verifiedAt,
            Instant usedAt, Instant invalidatedAt) { }
    public record Confirmation(String email, String signupVerificationToken, int expiresIn) { }
}
