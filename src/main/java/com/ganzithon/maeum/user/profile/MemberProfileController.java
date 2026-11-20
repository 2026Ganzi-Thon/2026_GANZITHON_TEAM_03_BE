package com.ganzithon.maeum.user.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ganzithon.maeum.global.api.ApiError;
import com.ganzithon.maeum.global.api.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Profile("auth")
public class MemberProfileController {
    private final MemberProfileService service;

    public MemberProfileController(MemberProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ApiResponse<MemberProfileService.Me> me(@AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(service.me(userId(jwt)));
    }

    @GetMapping("/nickname-availability")
    public ApiResponse<MemberProfileService.NicknameAvailability> nicknameAvailability(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "nickname", required = false) String nickname) {
        return new ApiResponse<>(service.nicknameAvailability(userId(jwt), nickname));
    }

    @PatchMapping("/me/initial-profile")
    public ApiResponse<MemberProfileService.ProfileView> initialProfile(@AuthenticationPrincipal Jwt jwt,
            @RequestBody ObjectNode body) {
        return new ApiResponse<>(service.save(userId(jwt), ProfilePatch.parse(body, true), true));
    }

    @PatchMapping("/me")
    public ApiResponse<MemberProfileService.ProfileView> updateProfile(@AuthenticationPrincipal Jwt jwt,
            @RequestBody ObjectNode body) {
        return new ApiResponse<>(service.save(userId(jwt), ProfilePatch.parse(body, false), false));
    }

    @PatchMapping("/me/onboarding")
    public ApiResponse<MemberProfileService.Onboarding> completeOnboarding(@AuthenticationPrincipal Jwt jwt,
            @RequestBody ObjectNode body) {
        JsonNode completed = body.get("completed");
        if (body.size() != 1 || completed == null || !completed.isBoolean() || !completed.booleanValue()) {
            throw ProfileException.invalid("completed", "완료 요청에는 completed를 true로 입력해주세요.");
        }
        return new ApiResponse<>(service.completeOnboarding(userId(jwt)));
    }

    @ExceptionHandler(ProfileException.class)
    ResponseEntity<ApiError> profileError(ProfileException exception) {
        return ResponseEntity.status(exception.status()).body(exception.error());
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
