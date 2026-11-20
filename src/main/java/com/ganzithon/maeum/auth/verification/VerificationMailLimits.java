package com.ganzithon.maeum.auth.verification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class VerificationMailLimits {
    private final JdbcTemplate jdbc;
    private final VerificationTokens tokens;
    private final int ipMaxPerMinute;

    public VerificationMailLimits(JdbcTemplate jdbc, VerificationTokens tokens,
            @Value("${app.verification.ip-max-per-minute:30}") int ipMaxPerMinute) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        if (ipMaxPerMinute < 1) throw new IllegalArgumentException("Invalid rate limit");
        this.ipMaxPerMinute = ipMaxPerMinute;
    }

    public Instant reserve(String mailboxKey, String clientIp, Instant requestedAt) {
        Instant now = requestedAt.truncatedTo(ChronoUnit.MICROS);
        String ipKey = "ip:" + clientIp;
        Limit ip = lock(ipKey, now);
        Limit mailbox = lock(mailboxKey, now);
        boolean sameWindow = now.isBefore(ip.windowStarted().plusSeconds(60));
        int count = sameWindow ? ip.count() : 0;
        if (count >= ipMaxPerMinute
                || (mailbox.lastSent() != null && now.isBefore(mailbox.lastSent().plusSeconds(60)))) {
            throw new VerificationException(HttpStatus.TOO_MANY_REQUESTS,
                    "AUTH_RATE_LIMITED", "잠시 후 인증 메일을 다시 요청해주세요.");
        }
        jdbc.update("UPDATE email_verification_limits SET last_sent_at=? WHERE limit_key=?",
                utc(now), tokens.hash(mailboxKey));
        jdbc.update("UPDATE email_verification_limits SET window_started_at=?, request_count=? WHERE limit_key=?",
                utc(sameWindow ? ip.windowStarted() : now), count + 1, tokens.hash(ipKey));
        return now;
    }

    public boolean isLatestReservation(String mailboxKey, Instant requestedAt) {
        LocalDateTime last = jdbc.queryForObject(
                "SELECT last_sent_at FROM email_verification_limits WHERE limit_key=?",
                LocalDateTime.class, tokens.hash(mailboxKey));
        return utc(requestedAt).equals(last);
    }

    public void lockMailbox(String mailboxKey, Instant now) {
        lock(mailboxKey, now);
    }

    private Limit lock(String key, Instant now) {
        String hash = tokens.hash(key);
        jdbc.update("""
                INSERT INTO email_verification_limits (limit_key, window_started_at, request_count)
                VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE limit_key=limit_key
                """, hash, utc(now));
        return jdbc.queryForObject("""
                SELECT window_started_at, request_count, last_sent_at
                FROM email_verification_limits WHERE limit_key=? FOR UPDATE
                """, (rs, row) -> {
                    var last = rs.getObject("last_sent_at", LocalDateTime.class);
                    return new Limit(rs.getObject("window_started_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                            rs.getInt("request_count"), last == null ? null : last.toInstant(ZoneOffset.UTC));
                }, hash);
    }

    private LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private record Limit(Instant windowStarted, int count, Instant lastSent) { }
}
