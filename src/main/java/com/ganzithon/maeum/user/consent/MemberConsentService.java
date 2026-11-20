package com.ganzithon.maeum.user.consent;

import com.ganzithon.maeum.auth.session.AuthFault;
import com.ganzithon.maeum.auth.session.AuthSettings;
import com.ganzithon.maeum.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("auth")
public class MemberConsentService {
    private final UserRepository users;
    private final ConsentHistory history;
    private final AuthSettings settings;
    private final Clock clock;

    public MemberConsentService(UserRepository users, ConsentHistory history, AuthSettings settings, Clock clock) {
        this.users = users;
        this.history = history;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConsentHistory.Entry> latest(long userId) {
        return history.latest(userId);
    }

    @Transactional
    public ConsentHistory.Entry updateMarketing(long userId, boolean agreed, String requestedVersion) {
        String currentVersion = settings.marketingVersion();
        if (agreed && !currentVersion.equals(requestedVersion)) {
            throw new AuthFault(HttpStatus.CONFLICT, "CONSENT_VERSION_MISMATCH",
                    "현재 마케팅 수신 동의 문서를 확인해주세요.");
        }

        // 같은 회원의 재시도와 동시에 들어온 변경을 순서대로 처리합니다.
        users.findForUpdate(userId).orElseThrow(() ->
                new AuthFault(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다."));
        var latest = history.latestMarketing(userId).orElse(null);
        String version = !agreed && latest != null ? latest.termsVersion() : currentVersion;
        if (latest != null && latest.agreed() == agreed && latest.termsVersion().equals(version)) {
            return latest;
        }

        Instant now = clock.instant();
        if (latest != null && now.isBefore(latest.recordedAt())) {
            now = latest.recordedAt();
        }
        return history.append(userId, ConsentHistory.MARKETING, version, agreed, now);
    }
}
