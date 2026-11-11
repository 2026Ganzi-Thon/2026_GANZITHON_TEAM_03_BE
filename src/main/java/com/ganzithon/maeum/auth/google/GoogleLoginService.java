package com.ganzithon.maeum.auth.google;

import com.ganzithon.maeum.auth.session.*;
import com.ganzithon.maeum.auth.verification.VerificationTokens;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ganzithon.maeum.auth.session.AuthSessionService.*;

@Service
@Profile("auth")
public class GoogleLoginService {
    private final JdbcTemplate jdbc;
    private final GoogleIdentityVerifier verifier;
    private final VerificationTokens random;
    private final Clock clock;
    private final AuthSessionService sessions;
    private final SignupConsents consents;
    private final TransactionTemplate transaction;
    public GoogleLoginService(JdbcTemplate jdbc, GoogleIdentityVerifier verifier, VerificationTokens random,
            Clock clock, AuthSessionService sessions, SignupConsents consents, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.verifier = verifier; this.random = random; this.clock = clock;
        this.sessions = sessions; this.consents = consents; this.transaction = new TransactionTemplate(manager);
    }

    public String challenge() {
        String nonce = random.issue();
        jdbc.update("INSERT INTO google_login_challenges(token_hash,expires_at) VALUES (?,?)",
                random.hash(nonce), utc(clock.instant().plusSeconds(300)));
        return nonce;
    }

    public Login login(String credential, String challenge, SignupConsents.Input consent) {
        if (challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}")) throw challengeInvalid();
        var identity = verifier.verify(credential, challenge); // 외부 키 조회 중 DB 잠금을 유지하지 않습니다.
        return transaction.execute(status -> {
            var challenges = jdbc.query("SELECT expires_at,used_at FROM google_login_challenges WHERE token_hash=? FOR UPDATE",
                    (rs, row) -> rs.getObject("used_at") == null && clock.instant().isBefore(instant(rs, "expires_at")), random.hash(challenge));
            if (challenges.isEmpty() || !challenges.getFirst()) throw challengeInvalid();
            String identityHash = random.hash("GOOGLE:" + identity.subject());
            jdbc.update("INSERT INTO auth_identity_locks(identity_hash) VALUES (?) ON DUPLICATE KEY UPDATE identity_hash=identity_hash", identityHash);
            jdbc.queryForObject("SELECT identity_hash FROM auth_identity_locks WHERE identity_hash=? FOR UPDATE", String.class, identityHash);
            var ids = jdbc.queryForList("SELECT user_id FROM social_accounts WHERE provider='GOOGLE' AND provider_user_id=?", Long.class, identity.subject());
            boolean isNew = ids.isEmpty();
            long userId;
            if (isNew) {
                validateConsent(consent);
                var now = utc(clock.instant());
                jdbc.update("INSERT INTO users(contact_email,profile_image_url,created_at,updated_at) VALUES (?,?,?,?)", identity.email(), identity.picture(), now, now);
                userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                jdbc.update("INSERT INTO social_accounts(user_id,provider,provider_user_id,created_at) VALUES (?,'GOOGLE',?,?)", userId, identity.subject(), now);
                consents.save(userId, consent);
            } else userId = ids.getFirst();
            jdbc.update("UPDATE google_login_challenges SET used_at=? WHERE token_hash=?", utc(clock.instant()), random.hash(challenge));
            Issued issued = sessions.create(userId);
            UserSummary user = jdbc.queryForObject("SELECT id,nickname,profile_completed_at,onboarding_completed_at FROM users WHERE id=?",
                    (rs, row) -> new UserSummary(Long.toString(rs.getLong("id")), rs.getString("nickname"),
                            rs.getObject("profile_completed_at") != null, rs.getObject("onboarding_completed_at") != null), userId);
            return new Login(new LoginBody(issued.accessToken(), "Bearer", 3600, user, isNew), issued.refreshToken());
        });
    }

    private void validateConsent(SignupConsents.Input consent) {
        if (consent == null || (consent.termsAgreed() == null && consent.privacyAgreed() == null)) {
            throw new AuthFault(HttpStatus.CONFLICT, "AUTH_SIGNUP_CONSENT_REQUIRED", "가입하려면 서비스 약관에 동의해주세요.");
        }
        consents.validate(consent);
    }
    private AuthFault challengeInvalid() { return new AuthFault(HttpStatus.UNAUTHORIZED, "AUTH_GOOGLE_CHALLENGE_INVALID", "로그인 화면에서 다시 시도해주세요."); }
    public record UserSummary(String userId, String nickname, boolean profileCompleted, boolean onboardingCompleted) { }
    public record LoginBody(String accessToken, String tokenType, int expiresIn, UserSummary user, boolean isNewUser) { }
    public record Login(LoginBody body, String refreshToken) { }
}
