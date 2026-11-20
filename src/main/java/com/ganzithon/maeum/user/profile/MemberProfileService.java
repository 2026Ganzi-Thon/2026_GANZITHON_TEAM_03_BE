package com.ganzithon.maeum.user.profile;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ganzithon.maeum.auth.repository.LocalCredentialRepository;
import com.ganzithon.maeum.user.domain.User;
import com.ganzithon.maeum.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("auth")
public class MemberProfileService {
    private final UserRepository users;
    private final LocalCredentialRepository localCredentials;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MemberProfileService(UserRepository users, LocalCredentialRepository localCredentials,
            JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.localCredentials = localCredentials;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Me me(long userId) {
        User user = users.findById(userId).orElseThrow(this::missingUser);
        var providers = new LinkedHashSet<String>();
        if (localCredentials.existsById(userId)) providers.add("LOCAL");
        providers.addAll(jdbc.queryForList("""
                SELECT DISTINCT provider FROM social_accounts WHERE user_id = ? ORDER BY provider
                """, String.class, userId));
        return new Me(ProfileView.from(user), user.getContactEmail(), List.copyOf(providers), user.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public NicknameAvailability nicknameAvailability(long userId, String raw) {
        String nickname = ProfilePatch.nickname(raw);
        return new NicknameAvailability(nickname, !users.existsByNicknameAndIdNot(nickname, userId));
    }

    @Transactional
    public ProfileView save(long userId, ProfilePatch patch, boolean initial) {
        User user = users.findForUpdate(userId).orElseThrow(this::missingUser);
        ProfileView current = ProfileView.from(user);
        if (initial && current.profileCompleted()) {
            if (patch.matches(current)) return current;
            throw new ProfileException(HttpStatus.CONFLICT, "USER_INITIAL_PROFILE_ALREADY_COMPLETED",
                    "초기 설정이 완료되었습니다. 내 정보 수정에서 변경해주세요.");
        }

        user.updateProfile(patch.apply("nickname", current.nickname()),
                patch.apply("introduction", current.introduction()), patch.apply("ageGroup", current.ageGroup()),
                patch.apply("regionCode", current.regionCode()));
        if (initial) user.completeProfile(now());
        flushProfile();
        return ProfileView.from(user);
    }

    @Transactional
    public Onboarding completeOnboarding(long userId) {
        User user = users.findForUpdate(userId).orElseThrow(this::missingUser);
        user.completeOnboarding(now());
        return new Onboarding(true, user.getOnboardingCompletedAt());
    }

    private void flushProfile() {
        try {
            users.flush();
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name != null && (name.equals("uk_users_nickname") || name.endsWith(".uk_users_nickname"))) {
                    throw new ProfileException(HttpStatus.CONFLICT, "USER_NICKNAME_DUPLICATED",
                            "이미 사용 중인 닉네임입니다.");
                }
            }
            throw exception;
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private ProfileException missingUser() {
        return new ProfileException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다.");
    }

    public record ProfileView(String userId, String nickname, String profileImageUrl, String introduction,
            String ageGroup, String regionCode, boolean profileCompleted, boolean onboardingCompleted) {
        static ProfileView from(User user) {
            return new ProfileView(Long.toString(user.getId()), user.getNickname(), user.getProfileImageUrl(),
                    user.getIntroduction(), user.getAgeGroup(), user.getRegionCode(),
                    user.isProfileCompleted(), user.isOnboardingCompleted());
        }
    }

    public record Me(@JsonUnwrapped ProfileView profile, String contactEmail, List<String> loginProviders,
            Instant createdAt) { }

    public record NicknameAvailability(String nickname, boolean available) { }

    public record Onboarding(boolean onboardingCompleted, Instant onboardingCompletedAt) { }
}
