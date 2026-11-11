package com.ganzithon.maeum.user.consent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ganzithon.maeum.global.api.ApiResponse;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/consents")
@Profile("auth")
public class MemberConsentController {
    private final MemberConsentService service;

    public MemberConsentController(MemberConsentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Consents> latest(@AuthenticationPrincipal Jwt jwt) {
        return new ApiResponse<>(new Consents(service.latest(Long.parseLong(jwt.getSubject()))));
    }

    @PatchMapping("/marketing")
    public ApiResponse<ConsentHistory.Entry> updateMarketing(@AuthenticationPrincipal Jwt jwt,
            @RequestBody ObjectNode body) {
        var agreed = body.path("agreed");
        var version = body.path("termsVersion");
        if (body.size() != 2 || !agreed.isBoolean() || !version.isTextual()
                || version.textValue().isBlank() || version.textValue().length() > 50) {
            throw new IllegalArgumentException("Invalid marketing consent request");
        }
        return new ApiResponse<>(service.updateMarketing(Long.parseLong(jwt.getSubject()),
                agreed.booleanValue(), version.textValue()));
    }

    public record Consents(List<ConsentHistory.Entry> items) { }
}
