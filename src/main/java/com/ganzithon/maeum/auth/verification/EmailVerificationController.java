package com.ganzithon.maeum.auth.verification;

import com.ganzithon.maeum.global.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/email-verifications")
@Profile("!bootstrap")
public class EmailVerificationController {
    private final EmailVerificationService service;

    public EmailVerificationController(EmailVerificationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> send(@Valid @RequestBody SendRequest request,
            HttpServletRequest servletRequest) {
        service.send(request.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(Map.of("message", "입력한 이메일로 인증 안내를 확인해주세요.")));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<EmailVerificationService.Confirmation>> confirm(@Valid @RequestBody ConfirmRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(service.confirm(request.token())));
    }

    public record SendRequest(@NotBlank @Size(max = 1024) String email) { }
    public record ConfirmRequest(@NotBlank @Size(max = 256) String token) { }
}
