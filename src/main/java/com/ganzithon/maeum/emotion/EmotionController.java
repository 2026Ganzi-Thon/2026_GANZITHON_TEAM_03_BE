package com.ganzithon.maeum.emotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
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
class EmotionController {
    private final EmotionService service;

    EmotionController(EmotionService service) {
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

    @GetMapping("/emotions")
    ResponseEntity<Object> emotions() {
        return ok(service.emotions());
    }

    @PostMapping("/emotion-records")
    ResponseEntity<Object> create(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode b) {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(service.create(user(jwt), b)));
    }

    @GetMapping("/emotion-records")
    ResponseEntity<Object> records(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(service.list(user(jwt), from, to, page, size));
    }

    @GetMapping("/emotion-records/{recordId}")
    ResponseEntity<Object> record(@AuthenticationPrincipal Jwt jwt, @PathVariable String recordId) {
        return ok(service.get(user(jwt), ActivityApi.id(recordId)));
    }

    @PatchMapping("/emotion-records/{recordId}")
    ResponseEntity<Object> patch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String recordId,
            @RequestBody JsonNode b) {
        return ok(service.patch(user(jwt), ActivityApi.id(recordId), b));
    }

    @DeleteMapping("/emotion-records/{recordId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String recordId) {
        service.delete(user(jwt), ActivityApi.id(recordId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
