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
    private final ComfortReplyService replyService;
    private final CheeseService cheeseService;

    ConcernController(
            ConcernService service,
            ComfortReplyService replyService,
            CheeseService cheeseService) {
        this.service = service;
        this.replyService = replyService;
        this.cheeseService = cheeseService;
    }

    private long user(Jwt jwt) {
        return ActivityApi.id(jwt.getSubject());
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(data));
    }

    private static ResponseEntity<Object> reply(Idempotency.Reply response) {
        return ResponseEntity.status(response.status())
                .cacheControl(CacheControl.noStore())
                .body(response.body());
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(data));
    }

    @GetMapping("/concerns")
    ResponseEntity<ApiResponse<ConcernService.ListResult>> list(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.list(user(jwt)));
    }

    @PostMapping("/concerns")
    ResponseEntity<Object> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode body) {
        return reply(service.create(user(jwt), key, body, ConcernCreateRequest.from(body)));
    }

    @GetMapping("/concerns/{concernId}")
    ResponseEntity<ApiResponse<ConcernService.Detail>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String concernId) {
        return ok(service.detail(user(jwt), ActivityApi.id(concernId)));
    }

    @PostMapping("/concerns/{concernId}/replies")
    ResponseEntity<ApiResponse<ComfortReplyService.ReplyResult>> createReply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String concernId,
            @RequestBody JsonNode body) {
        return created(replyService.reply(
                        user(jwt), ActivityApi.id(concernId), ComfortReplyRequest.from(body)));
    }

    @PostMapping("/concerns/{concernId}/open")
    ResponseEntity<ApiResponse<ComfortReplyService.ReceivedReplies>> open(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String concernId,
            @RequestBody(required = false) JsonNode body) {
        ActivityApi.noBody(body);
        return ok(replyService.open(user(jwt), ActivityApi.id(concernId)));
    }

    @GetMapping("/concerns/{concernId}/replies")
    ResponseEntity<ApiResponse<ComfortReplyService.ReceivedReplies>> replies(@AuthenticationPrincipal Jwt jwt, @PathVariable String concernId) {
        return ok(replyService.replies(user(jwt), ActivityApi.id(concernId)));
    }

    @GetMapping("/users/me/concerns")
    ResponseEntity<ApiResponse<CheeseService.PageResult<CheeseService.MineItem>>> mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(cheeseService.mine(user(jwt), page, size));
    }

    @GetMapping("/users/me/saved-concerns")
    ResponseEntity<ApiResponse<CheeseService.PageResult<CheeseService.SavedItem>>> saved(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(cheeseService.saved(user(jwt), page, size));
    }

    @PatchMapping("/concerns/{concernId}/save")
    ResponseEntity<ApiResponse<CheeseService.SaveResult>> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String concernId,
            @RequestBody(required = false) JsonNode body) {
        ActivityApi.noBody(body);
        return ok(cheeseService.save(user(jwt), ActivityApi.id(concernId)));
    }

    @PatchMapping("/concerns/{concernId}/unsave")
    ResponseEntity<ApiResponse<CheeseService.SaveResult>> unsave(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String concernId,
            @RequestBody(required = false) JsonNode body) {
        ActivityApi.noBody(body);
        return ok(cheeseService.unsave(user(jwt), ActivityApi.id(concernId)));
    }

    @DeleteMapping("/concerns/{concernId}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String concernId,
            @RequestBody(required = false) JsonNode body) {
        ActivityApi.noBody(body);
        service.delete(user(jwt), ActivityApi.id(concernId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
