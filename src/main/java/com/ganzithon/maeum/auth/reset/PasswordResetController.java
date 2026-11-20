package com.ganzithon.maeum.auth.reset;

import com.ganzithon.maeum.auth.domain.EmailAddress;
import com.ganzithon.maeum.global.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password-resets")
@Profile("auth")
public class PasswordResetController {
    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> request(@Valid @RequestBody RequestBodyData request,
            HttpServletRequest servletRequest) {
        service.request(request.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(Map.of("message", "재설정 가능한 계정이면 안내 메일이 발송됩니다.")));
    }

    @PatchMapping
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetBody request) {
        service.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record RequestBodyData(@NotBlank @Email @Size(max = 255) String email) {
        public RequestBodyData {
            email = new EmailAddress(email).value();
        }
    }

    public record ResetBody(@NotBlank @Size(max = 256) String token, @NotNull @Size(max = 1024) String newPassword) { }
}
