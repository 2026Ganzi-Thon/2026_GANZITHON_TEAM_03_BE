package com.ganzithon.maeum.auth.local;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ganzithon.maeum.auth.domain.EmailAddress;
import com.ganzithon.maeum.auth.session.AuthCookies;
import com.ganzithon.maeum.auth.session.SignupConsents;
import com.ganzithon.maeum.global.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("auth")
public class LocalAuthController {
    private final LocalAuthService service;
    private final AuthCookies cookies;

    public LocalAuthController(LocalAuthService service, AuthCookies cookies) {
        this.service = service;
        this.cookies = cookies;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid @RequestBody SignupRequest request) {
        var consent = new SignupConsents.Input(request.termsVersion(), request.termsAgreed(),
                request.privacyVersion(), request.privacyAgreed(), request.marketingVersion(), request.marketingAgreed());
        var result = service.signup(request.email(), request.password(), request.signupVerificationToken(), consent);
        return respond(result, HttpStatus.CREATED, true);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return respond(service.login(request.email(), request.password()), HttpStatus.OK, null);
    }

    private ResponseEntity<ApiResponse<LoginResponse>> respond(LocalAuthService.Result result, HttpStatus status,
            Boolean isNewUser) {
        var tokens = result.tokens();
        var body = new LoginResponse(tokens.accessToken(), "Bearer", 3600, result.user(), isNewUser);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(tokens.refreshToken()))
                .body(new ApiResponse<>(body));
    }

    public record SignupRequest(@NotBlank @Email @Size(max = 255) String email,
            @NotNull @Size(max = 1024) String password, @Size(max = 128) String signupVerificationToken,
            @Size(max = 50) String termsVersion, Boolean termsAgreed,
            @Size(max = 50) String privacyVersion, Boolean privacyAgreed,
            @Size(max = 50) String marketingVersion, Boolean marketingAgreed) {
        public SignupRequest {
            email = new EmailAddress(email).value();
        }
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 1024) String password) {
        public LoginRequest {
            email = new EmailAddress(email).value();
        }
    }

    public record LoginResponse(String accessToken, String tokenType, int expiresIn, LocalAuthService.UserSummary user,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isNewUser) { }
}
