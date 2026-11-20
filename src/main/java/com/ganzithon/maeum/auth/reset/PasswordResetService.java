package com.ganzithon.maeum.auth.reset;

import com.ganzithon.maeum.auth.repository.LocalCredentialRepository;
import com.ganzithon.maeum.auth.service.PasswordService;
import com.ganzithon.maeum.auth.service.RegistrationPasswordException;
import com.ganzithon.maeum.auth.session.AuthFault;
import com.ganzithon.maeum.auth.session.AuthSessionService;
import com.ganzithon.maeum.auth.verification.SmtpAuthMail;
import com.ganzithon.maeum.auth.verification.VerificationMailLimits;
import com.ganzithon.maeum.auth.verification.VerificationTokens;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static com.ganzithon.maeum.auth.session.AuthSessionService.instant;
import static com.ganzithon.maeum.auth.session.AuthSessionService.utc;

@Service
@Profile("auth")
public class PasswordResetService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String PURPOSE = "PASSWORD_RESET";
    private final JdbcTemplate jdbc;
    private final VerificationTokens tokens;
    private final VerificationMailLimits limits;
    private final LocalCredentialRepository credentials;
    private final PasswordService passwords;
    private final AuthSessionService sessions;
    private final SmtpAuthMail mail;
    private final TaskExecutor executor;
    private final Clock clock;
    private final String frontendUrl;
    private final TransactionTemplate transactions;

    public PasswordResetService(JdbcTemplate jdbc, VerificationTokens tokens, VerificationMailLimits limits,
            LocalCredentialRepository credentials, PasswordService passwords, AuthSessionService sessions,
            SmtpAuthMail mail, @Qualifier("passwordResetExecutor") TaskExecutor executor, Clock clock,
            @Value("${app.verification.password-reset-frontend-url:}") String frontendUrl,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.limits = limits;
        this.credentials = credentials;
        this.passwords = passwords;
        this.sessions = sessions;
        this.mail = mail;
        this.executor = executor;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(20);
    }

    public void request(String email, String clientIp) {
        mail.checkAvailable(frontendUrl);
        Instant reservedAt = transactions.execute(status -> limits.reserve(requestKey(email), clientIp, clock.instant()));
        try {
            // 모든 주소에 대해 동일하게 접수하고, 계정 확인과 SMTP는 워커에서 진행합니다.
            executor.execute(() -> sendIfEligible(email, reservedAt));
        } catch (TaskRejectedException exception) {
            throw SmtpAuthMail.unavailable();
        }
    }

    private void sendIfEligible(String email, Instant reservedAt) {
        try {
            transactions.executeWithoutResult(status -> {
                limits.lockMailbox(issueKey(email), clock.instant());
                if (!limits.isLatestReservation(requestKey(email), reservedAt)) return;
                credentials.findByEmail(email).filter(credential -> credential.getEmailVerifiedAt() != null
                                && !credential.getPasswordChangedAt().isAfter(reservedAt))
                        .ifPresent(credential -> issueAndSend(credential.getUserId(), email));
            });
        } catch (RuntimeException exception) {
            LOG.warn("비밀번호 재설정 메일 처리 실패");
        }
    }

    private void issueAndSend(long userId, String email) {
        Instant now = clock.instant();
        String raw = tokens.issue();
        invalidateOtherTokens(userId, now);
        jdbc.update("""
                INSERT INTO auth_verifications (user_id, email, purpose, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, email, PURPOSE, tokens.hash(raw), utc(now.plusSeconds(1800)), utc(now));
        mail.sendLink(email, raw, frontendUrl, "마음 돌봄 비밀번호 재설정",
                "30분 안에 아래 링크에서 새 비밀번호를 설정해주세요.");
    }

    @Transactional
    public void reset(String raw, String newPassword) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String hash = tokens.hash(raw);
        ResetToken initial = find(hash, false);
        limits.lockMailbox(issueKey(initial.email()), clock.instant());
        ResetToken token = find(hash, true);
        Instant now = clock.instant();
        validate(token, now);
        var credential = credentials.findByEmailForUpdate(token.email())
                .filter(value -> value.getEmailVerifiedAt() != null && value.getUserId() == token.userId())
                .orElseThrow(this::invalid);

        try {
            credential.changePassword(passwords.encodeForRegistration(newPassword), now);
        } catch (RegistrationPasswordException exception) {
            throw new AuthFault(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_POLICY_VIOLATION", exception.getMessage());
        }
        jdbc.update("UPDATE auth_verifications SET used_at=? WHERE token_hash=?", utc(now), hash);
        invalidateOtherTokens(token.userId(), now);
        sessions.revokeAll(token.userId(), now);
    }

    private ResetToken find(String hash, boolean lock) {
        var rows = jdbc.query("""
                SELECT user_id, email, expires_at, used_at, invalidated_at
                FROM auth_verifications WHERE token_hash=? AND purpose=? AND user_id IS NOT NULL
                """ + (lock ? " FOR UPDATE" : ""), (rs, row) -> new ResetToken(rs.getLong("user_id"),
                rs.getString("email"), instant(rs, "expires_at"), instant(rs, "used_at"), instant(rs, "invalidated_at")),
                hash, PURPOSE);
        if (rows.isEmpty()) throw invalid();
        return rows.getFirst();
    }

    private void validate(ResetToken token, Instant now) {
        if (token.usedAt() != null || token.invalidatedAt() != null) {
            throw new AuthFault(HttpStatus.CONFLICT, "AUTH_PASSWORD_RESET_USED", "이미 사용했거나 폐기된 링크입니다.");
        }
        if (!now.isBefore(token.expiresAt())) {
            throw new AuthFault(HttpStatus.GONE, "AUTH_PASSWORD_RESET_EXPIRED", "링크가 만료되었습니다. 다시 요청해주세요.");
        }
    }

    private void invalidateOtherTokens(long userId, Instant now) {
        jdbc.update("""
                UPDATE auth_verifications SET invalidated_at=?
                WHERE user_id=? AND purpose=? AND used_at IS NULL AND invalidated_at IS NULL
                """, utc(now), userId, PURPOSE);
    }

    private AuthFault invalid() {
        return new AuthFault(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_RESET_INVALID", "올바르지 않은 재설정 링크입니다.");
    }

    private String requestKey(String email) { return "password-reset-request:" + email; }

    private String issueKey(String email) { return "password-reset-issue:" + email; }

    private record ResetToken(long userId, String email, Instant expiresAt, Instant usedAt, Instant invalidatedAt) { }
}
