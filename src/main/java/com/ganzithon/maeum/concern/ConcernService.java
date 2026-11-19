package com.ganzithon.maeum.concern;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.Idempotency;
import com.ganzithon.maeum.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Profile("auth")
@Transactional
class ConcernService {
    private static final int LIST_LIMIT = 100;

    private final ConcernRepository concerns;
    private final ComfortReplyRepository replies;
    private final UserRepository users;
    private final Clock clock;
    private final Idempotency idem;
    private final long maturingHours;

    ConcernService(
            ConcernRepository concerns,
            ComfortReplyRepository replies,
            UserRepository users,
            Clock c,
            Idempotency i,
            @Value("${app.activity.concern-maturing-hours:24}") long maturingHours) {
        this.concerns = concerns;
        this.replies = replies;
        this.users = users;
        clock = c;
        idem = i;
        this.maturingHours = maturingHours;
    }

    record ListItem(String concernId, String preview, ComfortType comfortType, Instant createdAt) {}

    record ListResult(List<ListItem> items, int count) {}

    record CreateResult(
            String concernId, ConcernStatus status, Instant maturesAt, Instant createdAt) {}

    record Detail(
            String concernId,
            String content,
            ComfortType comfortType,
            Instant createdAt,
            boolean mine,
            ConcernStatus status,
            Instant maturesAt,
            boolean replyable,
            boolean alreadyReplied) {}

    @Transactional(readOnly = true)
    ListResult list(long user) {
        var now = ConcernRules.now(clock);
        var items =
                concerns.findReplyCandidates(user, now, LIST_LIMIT).stream()
                        .map(
                                c ->
                                        new ListItem(
                                                c.getId().toString(),
                                                c.preview(),
                                                c.getComfortType(),
                                                c.getCreatedAt()))
                        .toList();
        return new ListResult(items, items.size());
    }

    Idempotency.Reply create(
            long user, String key, JsonNode body, ConcernCreateRequest request) {
        // 같은 사용자의 멱등 요청을 직렬화하려고 사용자 행을 먼저 잠급니다.
        users.findForUpdate(user).orElseThrow(() -> ActivityApi.problem(404, "USER_NOT_FOUND"));
        var now = ConcernRules.now(clock);
        return idem.execute(
                user,
                key,
                "POST /api/v1/concerns",
                body,
                now,
                201,
                () -> {
                    var matures = now.plus(Duration.ofHours(maturingHours));
                    var concern =
                            concerns.save(
                                    new Concern(
                                            user,
                                            request.content(),
                                            request.comfortType(),
                                            matures,
                                            now));
                    return new CreateResult(
                            concern.getId().toString(), ConcernStatus.MATURING, matures, now);
                });
    }

    @Transactional(readOnly = true)
    Detail detail(long user, long id) {
        var now = ConcernRules.now(clock);
        var concern =
                concerns.findById(id)
                        .orElseThrow(() -> ActivityApi.problem(404, "CONCERN_NOT_FOUND"));
        boolean mine = concern.isOwnedBy(user);
        boolean replied = !mine && replies.existsByConcernIdAndAuthorId(id, user);
        var status = concern.statusAt(now);
        return new Detail(
                concern.getId().toString(),
                concern.getContent(),
                concern.getComfortType(),
                concern.getCreatedAt(),
                mine,
                status,
                mine ? concern.getMaturesAt() : null,
                !mine && !replied && status == ConcernStatus.MATURING,
                replied);
    }

    void delete(long user, long id) {
        if (concerns.deleteMine(id, user) == 0) throw ActivityApi.problem(404, "CONCERN_NOT_FOUND");
    }
}
