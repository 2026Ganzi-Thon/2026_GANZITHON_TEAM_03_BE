package com.ganzithon.maeum.user.consent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import static com.ganzithon.maeum.auth.session.AuthSessionService.instant;
import static com.ganzithon.maeum.auth.session.AuthSessionService.utc;

@Repository
@Profile("auth")
public class ConsentHistory {
    public static final String TERMS = "TERMS";
    public static final String PRIVACY = "PRIVACY";
    public static final String MARKETING = "MARKETING";

    private final JdbcTemplate jdbc;

    public ConsentHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Entry> latest(long userId) {
        return jdbc.query("""
                SELECT consent_type, terms_version, agreed, recorded_at
                FROM (
                    SELECT consent_type, terms_version, agreed, recorded_at,
                           ROW_NUMBER() OVER (PARTITION BY consent_type ORDER BY recorded_at DESC, id DESC) AS position_in_type
                    FROM user_consents WHERE user_id = ?
                ) history
                WHERE position_in_type = 1
                ORDER BY CASE consent_type WHEN 'TERMS' THEN 1 WHEN 'PRIVACY' THEN 2 WHEN 'MARKETING' THEN 3 ELSE 4 END,
                         consent_type
                """, this::entry, userId);
    }

    public Optional<Entry> latestMarketing(long userId) {
        return jdbc.query("""
                SELECT consent_type, terms_version, agreed, recorded_at
                FROM user_consents WHERE user_id = ? AND consent_type = ?
                ORDER BY recorded_at DESC, id DESC LIMIT 1
                """, this::entry, userId, MARKETING).stream().findFirst();
    }

    public Entry append(long userId, String type, String version, boolean agreed, Instant now) {
        Instant recordedAt = now.truncatedTo(ChronoUnit.MICROS);
        jdbc.update("""
                INSERT INTO user_consents (user_id, consent_type, terms_version, agreed, recorded_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, type, version, agreed, utc(recordedAt));
        return new Entry(type, version, agreed, recordedAt);
    }

    private Entry entry(ResultSet rs, int row) throws SQLException {
        return new Entry(rs.getString("consent_type"), rs.getString("terms_version"),
                rs.getBoolean("agreed"), instant(rs, "recorded_at"));
    }

    public record Entry(String consentType, String termsVersion, boolean agreed, Instant recordedAt) { }
}
