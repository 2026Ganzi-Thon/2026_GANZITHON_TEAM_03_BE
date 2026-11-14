package com.ganzithon.maeum.concern;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;
import com.ganzithon.maeum.global.activity.Idempotency;

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

    private final ActivityStore store;
    private final Clock clock;
    private final Idempotency idem;
    private final long maturingHours;

    ConcernService(
            ActivityStore s,
            Clock c,
            Idempotency i,
            @Value("${app.activity.concern-maturing-hours:24}") long maturingHours) {
        store = s;
        clock = c;
        idem = i;
        this.maturingHours = maturingHours;
    }

    record ListItem(String concernId, String preview, String comfortType, Instant createdAt) {}

    record ListResult(List<ListItem> items, int count) {}

    record CreateResult(String concernId, String status, Instant maturesAt, Instant createdAt) {}

    record Detail(
            String concernId,
            String content,
            String comfortType,
            Instant createdAt,
            boolean mine,
            String status,
            Instant maturesAt,
            boolean replyable,
            boolean alreadyReplied) {}

    @Transactional(readOnly = true)
    ListResult list(long user) {
        var now = ConcernRules.now(clock);
        var items =
                store
                        .list(
                                "SELECT c.id,c.content,c.comfort_type,c.created_at FROM concerns c"
                                    + " WHERE c.user_id<>? AND c.matures_at>? AND NOT EXISTS (SELECT 1"
                                    + " FROM comfort_replies r WHERE r.concern_id=c.id AND"
                                    + " r.author_id=?) ORDER BY RAND() LIMIT ?",
                                user,
                                ActivityApi.ts(now),
                                user,
                                LIST_LIMIT)
                        .stream()
                        .map(
                                r ->
                                        new ListItem(
                                                ActivityApi.sid(r.get("id")),
                                                ConcernRules.preview(r),
                                                (String) r.get("comfort_type"),
                                                ActivityApi.instant(r.get("created_at"))))
                        .toList();
        return new ListResult(items, items.size());
    }

    Idempotency.Reply create(
            long user, String key, JsonNode body, ConcernCreateRequest request) {
        store.lockUser(user);
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
                    long id =
                            store.insert(
                                    "INSERT INTO"
                                        + " concerns(user_id,content,comfort_type,matures_at,created_at)"
                                        + " VALUES(?,?,?,?,?)",
                                    user,
                                    request.content(),
                                    request.comfortType(),
                                    ActivityApi.ts(matures),
                                    ActivityApi.ts(now));
                    return new CreateResult(Long.toString(id), "MATURING", matures, now);
                });
    }

    @Transactional(readOnly = true)
    Detail detail(long user, long id) {
        var now = ConcernRules.now(clock);
        var r =
                store.required(
                        "SELECT * FROM concerns WHERE id=?", "CONCERN_NOT_FOUND", id);
        boolean mine = ((Number) r.get("user_id")).longValue() == user;
        boolean replied =
                !mine
                        && store.one(
                                        "SELECT id FROM comfort_replies WHERE concern_id=? AND"
                                            + " author_id=?",
                                        id,
                                        user)
                                != null;
        String status = ConcernRules.status(r, now);
        return new Detail(
                ActivityApi.sid(r.get("id")),
                (String) r.get("content"),
                (String) r.get("comfort_type"),
                ActivityApi.instant(r.get("created_at")),
                mine,
                status,
                mine ? ActivityApi.instant(r.get("matures_at")) : null,
                !mine && !replied && status.equals("MATURING"),
                replied);
    }

    void delete(long user, long id) {
        if (store.update("DELETE FROM concerns WHERE id=? AND user_id=?", id, user) == 0)
            throw ActivityApi.problem(404, "CONCERN_NOT_FOUND");
    }
}
