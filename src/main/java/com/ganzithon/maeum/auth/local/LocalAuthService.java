package com.ganzithon.maeum.auth.local;

import com.ganzithon.maeum.auth.repository.LocalCredentialRepository;
import com.ganzithon.maeum.auth.service.DuplicateLocalEmailException;
import com.ganzithon.maeum.auth.service.LocalMemberStore;
import com.ganzithon.maeum.auth.service.PasswordService;
import com.ganzithon.maeum.auth.session.AuthFault;
import com.ganzithon.maeum.auth.session.AuthSessionService;
import com.ganzithon.maeum.auth.session.SignupConsents;
import com.ganzithon.maeum.auth.verification.EmailVerificationService;
import com.ganzithon.maeum.auth.verification.VerificationException;
import com.ganzithon.maeum.user.domain.User;
import com.ganzithon.maeum.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("auth")
public class LocalAuthService {
    private final LocalMemberStore members;
    private final LocalCredentialRepository credentials;
    private final UserRepository users;
    private final PasswordService passwords;
    private final EmailVerificationService verifications;
    private final SignupConsents consents;
    private final AuthSessionService sessions;
    private final String dummyPasswordHash;

    public LocalAuthService(LocalMemberStore members, LocalCredentialRepository credentials, UserRepository users,
            PasswordService passwords, EmailVerificationService verifications, SignupConsents consents,
            AuthSessionService sessions) {
        this.members = members;
        this.credentials = credentials;
        this.users = users;
        this.passwords = passwords;
        this.verifications = verifications;
        this.consents = consents;
        this.sessions = sessions;
        this.dummyPasswordHash = passwords.encodeForRegistration("Dummy1!" + UUID.randomUUID());
    }

    @Transactional
    public Result signup(String email, String password, String proof, SignupConsents.Input consent) {
        consents.validate(consent);
        Instant verifiedAt = consumeProof(proof, email);
        User user = createMember(email, password, verifiedAt);
        consents.save(user.getId(), consent);
        return issueSession(user);
    }

    @Transactional
    public Result login(String email, String password) {
        var credential = credentials.findByEmailForUpdate(email).orElse(null);
        // 없는 계정도 같은 비용으로 비밀번호를 확인합니다.
        String hash = credential == null ? dummyPasswordHash : credential.getPasswordHash();
        if (!passwords.matches(password, hash) || credential == null || credential.getEmailVerifiedAt() == null) {
            throw loginFailed();
        }
        User user = users.findById(credential.getUserId()).orElseThrow(this::loginFailed);
        return issueSession(user);
    }

    private Instant consumeProof(String proof, String email) {
        try {
            return verifications.consumeSignupProof(proof, email);
        } catch (VerificationException exception) {
            throw new AuthFault(HttpStatus.UNAUTHORIZED, "AUTH_SIGNUP_VERIFICATION_INVALID",
                    "이메일 인증을 다시 진행해주세요.");
        }
    }

    private User createMember(String email, String password, Instant verifiedAt) {
        try {
            return members.createVerified(email, password, verifiedAt);
        } catch (DuplicateLocalEmailException exception) {
            throw duplicateEmail();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if ("uk_local_credentials_email".equals(constraint)
                        || "local_credentials.uk_local_credentials_email".equals(constraint)) {
                    throw duplicateEmail();
                }
            }
            throw exception;
        }
    }

    private Result issueSession(User user) {
        return new Result(sessions.create(user.getId()), new UserSummary(Long.toString(user.getId()),
                user.getNickname(), user.isProfileCompleted(), user.isOnboardingCompleted()));
    }

    private AuthFault duplicateEmail() {
        return new AuthFault(HttpStatus.CONFLICT, "AUTH_EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다.");
    }

    private AuthFault loginFailed() {
        return new AuthFault(HttpStatus.UNAUTHORIZED, "AUTH_LOGIN_FAILED", "이메일 또는 비밀번호를 확인해주세요.");
    }

    public record Result(AuthSessionService.Issued tokens, UserSummary user) { }

    public record UserSummary(String userId, String nickname, boolean profileCompleted, boolean onboardingCompleted) { }
}
