package com.ganzithon.maeum.auth.session;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@Profile("auth")
public class AuthCookies {
    public static final String REFRESH = "refreshToken";
    public static final String CHALLENGE = "googleLoginChallenge";
    private final AuthSettings settings;
    public AuthCookies(AuthSettings settings) { this.settings = settings; }
    public String refresh(String value) { return cookie(REFRESH, value, "/api/v1/auth", 1209600); }
    public String clearRefresh() { return cookie(REFRESH, "", "/api/v1/auth", 0); }
    public String challenge(String value) { return cookie(CHALLENGE, value, "/api/v1/auth/google", 300); }
    public String clearChallenge() { return cookie(CHALLENGE, "", "/api/v1/auth/google", 0); }
    private String cookie(String name, String value, String path, long maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(true).sameSite(settings.cookieSameSite())
                .path(path).maxAge(maxAge).build().toString();
    }
}
