package com.ganzithon.maeum.auth.google;

import com.ganzithon.maeum.auth.session.*;
import com.ganzithon.maeum.global.api.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("auth")
public class GoogleAuthController {
    private final GoogleLoginService google;
    private final AuthSessionService sessions;
    private final AuthCookies cookies;
    public GoogleAuthController(GoogleLoginService google, AuthSessionService sessions, AuthCookies cookies) {
        this.google = google; this.sessions = sessions; this.cookies = cookies;
    }
    @PostMapping("/google/challenges")
    public ResponseEntity<?> challenge() {
        String nonce = google.challenge();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE, cookies.challenge(nonce))
                .body(new ApiResponse<>(Map.of("nonce", nonce, "expiresIn", 300)));
    }
    @PostMapping("/google")
    public ResponseEntity<?> google(@Valid @RequestBody GoogleRequest request,
            @CookieValue(name = AuthCookies.CHALLENGE, required = false) String challenge) {
        var result = google.login(request.credential(), challenge, new SignupConsents.Input(
                request.termsVersion(), request.termsAgreed(), request.privacyVersion(), request.privacyAgreed(),
                request.marketingVersion(), request.marketingAgreed()));
        return ResponseEntity.status(result.body().isNewUser() ? 201 : 200).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(result.refreshToken()), cookies.clearChallenge())
                .body(new ApiResponse<>(result.body()));
    }
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = AuthCookies.REFRESH, required = false) String refresh) {
        try {
            var result = sessions.rotate(refresh);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE, cookies.refresh(result.refreshToken()))
                    .body(new ApiResponse<>(Map.of("accessToken", result.accessToken(), "tokenType", "Bearer", "expiresIn", 3600)));
        } catch (AuthFault fault) {
            return ResponseEntity.status(fault.getStatus()).cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh())
                    .body(new ApiError(fault.getCode(), fault.getMessage(), List.of()));
        }
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String refresh) {
        sessions.logout(refresh);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clearRefresh(), cookies.clearChallenge()).build();
    }
    public record GoogleRequest(@NotBlank @Size(max = 16000) String credential,
            @Size(max = 50) String termsVersion, Boolean termsAgreed,
            @Size(max = 50) String privacyVersion, Boolean privacyAgreed,
            @Size(max = 50) String marketingVersion, Boolean marketingAgreed) { }
}
