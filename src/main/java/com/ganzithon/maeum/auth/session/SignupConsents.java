package com.ganzithon.maeum.auth.session;

import com.ganzithon.maeum.user.consent.ConsentHistory;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("auth")
public class SignupConsents {
    private final ConsentHistory history;
    private final Clock clock;
    private final AuthSettings settings;

    public SignupConsents(ConsentHistory history, Clock clock, AuthSettings settings) {
        this.history = history;
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
        var now = clock.instant();
        history.append(userId, ConsentHistory.TERMS, settings.termsVersion(), true, now);
        history.append(userId, ConsentHistory.PRIVACY, settings.privacyVersion(), true, now);
        history.append(userId, ConsentHistory.MARKETING, settings.marketingVersion(),
                Boolean.TRUE.equals(input.marketingAgreed()), now);
    }

    public record Input(String termsVersion, Boolean termsAgreed, String privacyVersion, Boolean privacyAgreed,
            String marketingVersion, Boolean marketingAgreed) { }
}
