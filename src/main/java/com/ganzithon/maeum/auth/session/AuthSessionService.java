package com.ganzithon.maeum.auth.session;

import com.ganzithon.maeum.auth.verification.VerificationTokens;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("auth")
public class AuthSessionService {
    private final JdbcTemplate jdbc;
    private final VerificationTokens random;
    private final AccessTokens access;
    private final Clock clock;
    public AuthSessionService(JdbcTemplate jdbc, VerificationTokens random, AccessTokens access, Clock clock) {
        this.jdbc = jdbc; this.random = random; this.access = access; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Issued create(long userId) {
        String session = UUID.randomUUID().toString();
        Instant now = clock.instant();
        jdbc.update("INSERT INTO auth_sessions(id,user_id,created_at) VALUES (?,?,?)", session, userId, utc(now));
        return issue(userId, session, now);
    }

    @Transactional(noRollbackFor = RefreshReuseException.class)
    public Issued rotate(String raw) {
        Token original = find(raw);
        Session session = lockSession(original.session());
        Token token = find(raw, true); // MySQL snapshot 대신 잠금 읽기로 최신 소비 상태를 확인합니다.
        Instant now = clock.instant();
        if (session.revoked() != null) throw invalid();
        if (token.used() != null) {
            jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE id=?", utc(now), session.id());
            throw new RefreshReuseException(); // 이 예외에서만 폐기 변경을 커밋합니다.
        }
        if (!now.isBefore(token.expiry())) {
            throw new AuthFault(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_EXPIRED", "다시 로그인해주세요.");
        }
        jdbc.update("UPDATE refresh_tokens SET used_at=? WHERE token_hash=?", utc(now), random.hash(raw));
        return issue(session.userId(), session.id(), now);
    }

    @Transactional
    public void logout(String raw) {
        if (!validFormat(raw)) return;
        var tokens = jdbc.queryForList("SELECT session_id FROM refresh_tokens WHERE token_hash=?", String.class, random.hash(raw));
        if (tokens.isEmpty()) return;
        Session session = lockSession(tokens.getFirst());
        if (session.revoked() == null) jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE id=?", utc(clock.instant()), session.id());
    }

    public boolean active(String sessionId, String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM auth_sessions WHERE id=? AND user_id=? AND revoked_at IS NULL)",
                Boolean.class, sessionId, userId));
    }

    private Issued issue(long userId, String session, Instant now) {
        String refresh = random.issue();
        jdbc.update("INSERT INTO refresh_tokens(token_hash,session_id,expires_at,created_at) VALUES (?,?,?,?)",
                random.hash(refresh), session, utc(now.plusSeconds(1209600)), utc(now));
        return new Issued(access.issue(userId, session), refresh);
    }
    private Token find(String raw) {
        return find(raw, false);
    }
    private Token find(String raw, boolean lock) {
        if (!validFormat(raw)) throw invalid();
        var result = jdbc.query("SELECT session_id, expires_at, used_at FROM refresh_tokens WHERE token_hash=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Token(rs.getString(1), instant(rs, "expires_at"), instant(rs, "used_at")), random.hash(raw));
        if (result.isEmpty()) throw invalid();
        return result.getFirst();
    }
    private Session lockSession(String id) {
        var result = jdbc.query("SELECT id,user_id,revoked_at FROM auth_sessions WHERE id=? FOR UPDATE",
                (rs, row) -> new Session(rs.getString(1), rs.getLong(2), instant(rs, "revoked_at")), id);
        if (result.isEmpty()) throw invalid();
        return result.getFirst();
    }
    private boolean validFormat(String value) { return value != null && value.matches("[A-Za-z0-9_-]{43}"); }
    private AuthFault invalid() { return new AuthFault(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_TOKEN_INVALID", "다시 로그인해주세요."); }
    public static LocalDateTime utc(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    public static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
    private record Token(String session, Instant expiry, Instant used) { }
    private record Session(String id, long userId, Instant revoked) { }
    public record Issued(String accessToken, String refreshToken) { }
}
