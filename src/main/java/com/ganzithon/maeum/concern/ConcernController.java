package com.ganzithon.maeum.concern;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.Idempotency;
import com.ganzithon.maeum.global.api.ApiResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("auth")
@RequestMapping("/api/v1")
class ConcernController {
    private final ConcernService service;

    ConcernController(ConcernService service) {
        this.service = service;
    }

    private long user(Jwt jwt) {
        return ActivityApi.id(jwt.getSubject());
    }

    private ResponseEntity<Object> ok(Object data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(data));
    }

    private ResponseEntity<Object> reply(Idempotency.Reply response) {
        return ResponseEntity.status(response.status())
                .cacheControl(CacheControl.noStore())
                .body(response.body());
    }

    @GetMapping("/concerns")
    ResponseEntity<Object> list(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.list(user(jwt)));
    }

    @PostMapping("/concerns")
    ResponseEntity<Object> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode body) {
        return reply(service.create(user(jwt), key, body));
    }

    @GetMapping("/concerns/{concernId}")
    ResponseEntity<Object> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String concernId) {
        return ok(service.detail(user(jwt), ActivityApi.id(concernId)));
    }
}
