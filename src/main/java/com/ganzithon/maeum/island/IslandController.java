package com.ganzithon.maeum.island;

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
class IslandController {
    private final IslandService service;

    IslandController(IslandService service) {
        this.service = service;
    }

    private long user(Jwt jwt) {
        return ActivityApi.id(jwt.getSubject());
    }

    @GetMapping("/island/items")
    ResponseEntity<ApiResponse<IslandItemsResponse>> items(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(service.items(user(jwt))));
    }

    /** 같은 키 재요청은 저장된 첫 응답(JSON)을 그대로 돌려주므로 본문 타입은 Object입니다. */
    @PostMapping("/island/items/{itemId}/purchases")
    ResponseEntity<Object> purchase(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String itemId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) JsonNode body) {
        ActivityApi.noBody(body);
        var reply = service.purchase(user(jwt), ActivityApi.id(itemId), key);
        return ResponseEntity.status(reply.status())
                .cacheControl(CacheControl.noStore())
                .body(reply.body());
    }
}
