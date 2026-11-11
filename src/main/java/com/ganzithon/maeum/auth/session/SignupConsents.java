package com.ganzithon.maeum.auth.session;

import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static com.ganzithon.maeum.auth.session.AuthSessionService.utc;

@Component
@Profile("auth")
public class SignupConsents {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuthSettings settings;

    public SignupConsents(JdbcTemplate jdbc, Clock clock, AuthSettings settings) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
    }

    public void validate(Input input) {
        if (!Boolean.TRUE.equals(input.termsAgreed()) || !Boolean.TRUE.equals(input.privacyAgreed())
                || !settings.termsVersion().equals(input.termsVersion())
                || !settings.privacyVersion().equals(input.privacyVersion())
                || (Boolean.TRUE.equals(input.marketingAgreed())
                    && !settings.marketingVersion().equals(input.marketingVersion()))) {
            throw new AuthFault(HttpStatus.BAD_REQUEST, "AUTH_REQUIRED_CONSENT_MISSING",
                    "필수 약관 동의와 문서 버전을 확인해주세요.");
        }
    }

    public void save(long userId, Input input) {
        insert(userId, "TERMS", settings.termsVersion(), true);
        insert(userId, "PRIVACY", settings.privacyVersion(), true);
        insert(userId, "MARKETING", settings.marketingVersion(), Boolean.TRUE.equals(input.marketingAgreed()));
    }

    private void insert(long userId, String type, String version, boolean agreed) {
        jdbc.update("""
                INSERT INTO user_consents (user_id, consent_type, terms_version, agreed, recorded_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, type, version, agreed, utc(clock.instant()));
    }

    public record Input(String termsVersion, Boolean termsAgreed, String privacyVersion, Boolean privacyAgreed,
            String marketingVersion, Boolean marketingAgreed) { }
}
