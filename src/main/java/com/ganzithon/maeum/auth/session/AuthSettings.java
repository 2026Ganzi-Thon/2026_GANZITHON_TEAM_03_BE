package com.ganzithon.maeum.auth.session;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthSettings(String jwtSecretBase64, String googleClientId, List<String> allowedOrigins,
        String cookieSameSite, String termsVersion, String privacyVersion, String marketingVersion) {
    public AuthSettings {
        if (googleClientId == null || googleClientId.isBlank() || allowedOrigins == null || allowedOrigins.isEmpty()
                || termsVersion == null || termsVersion.isBlank() || privacyVersion == null || privacyVersion.isBlank()
                || marketingVersion == null || marketingVersion.isBlank()) {
            throw new IllegalArgumentException("auth 프로필의 Client ID, Origin, 약관 버전 설정이 필요합니다.");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (uri.getHost() == null || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())) {
                throw new IllegalArgumentException("허용 Origin에는 정확한 HTTPS 출처를 지정해주세요.");
            }
        }
        if (!List.of("Lax", "Strict", "None").contains(cookieSameSite)) {
            throw new IllegalArgumentException("Cookie SameSite는 Lax, Strict, None 중 하나여야 합니다.");
        }
    }
}
