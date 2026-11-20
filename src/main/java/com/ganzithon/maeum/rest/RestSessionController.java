package com.ganzithon.maeum.rest;

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
class RestSessionController {
    private final RestService service;

    RestSessionController(RestService service) {
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

    @PostMapping("/rest-sessions")
    ResponseEntity<Object> start(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode b) {
        return reply(service.start(user(jwt), key, b));
    }

    @GetMapping("/rest-sessions/current")
    ResponseEntity<Object> current(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.current(user(jwt)));
    }

    @PostMapping("/rest-sessions/{sessionId}/cancel")
    ResponseEntity<Object> cancelRest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sessionId,
            @RequestBody(required = false) JsonNode body) {
        ActivityApi.noBody(body);
        return ok(service.cancel(user(jwt), ActivityApi.id(sessionId)));
    }

    @PatchMapping("/rest-sessions/{sessionId}/after-memo")
    ResponseEntity<Object> memo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String sessionId,
            @RequestBody JsonNode b) {
        return ok(service.memo(user(jwt), ActivityApi.id(sessionId), b));
    }
}
