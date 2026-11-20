package com.ganzithon.maeum.care;

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
class CareController {
    private final CareService service;

    CareController(CareService service) {
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

    @GetMapping("/care-actions")
    ResponseEntity<Object> actions() {
        return ok(service.actions());
    }

    @GetMapping("/care-completions/today")
    ResponseEntity<Object> today(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.today(user(jwt)));
    }

    @PostMapping("/care-actions/{careActionId}/completions")
    ResponseEntity<Object> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String careActionId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) JsonNode body) {
        ActivityApi.noBody(body);
        return reply(service.complete(user(jwt), ActivityApi.id(careActionId), key));
    }

    @PostMapping("/care-completions/{completionId}/cancel")
    ResponseEntity<Object> cancelCare(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String completionId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode b) {
        return reply(service.cancel(user(jwt), ActivityApi.id(completionId), key, b));
    }

    @GetMapping("/wool-wallet")
    ResponseEntity<Object> wallet(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.wallet(user(jwt)));
    }

    @GetMapping("/wool-transactions")
    ResponseEntity<Object> transactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(service.transactions(user(jwt), page, size));
    }
}
