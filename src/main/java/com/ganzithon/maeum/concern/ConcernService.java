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
    private static final int CONTENT_LIMIT = 1000;
    private static final int PREVIEW_LENGTH = 100;
    private static final int LIST_LIMIT = 100;
    private static final Set<String> COMFORT_TYPES = Set.of("EMPATHY", "EXPERIENCE", "ADVICE");

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

    private static String status(Map<String, Object> r, Instant now) {
        if (r.get("saved_at") != null) return "SAVED";
        if (r.get("opened_at") != null) return "OPENED";
        return now.isBefore(ActivityApi.instant(r.get("matures_at"))) ? "MATURING" : "MATURED";
    }

    @Transactional(readOnly = true)
    Object list(long user) {
        var now = clock.instant();
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
                                r -> {
                                    String content = (String) r.get("content");
                                    int end =
                                            content.offsetByCodePoints(
                                                    0,
                                                    Math.min(
                                                            PREVIEW_LENGTH,
                                                            content.codePointCount(
                                                                    0, content.length())));
                                    return ActivityApi.obj(
                                            "concernId",
                                            ActivityApi.sid(r.get("id")),
                                            "preview",
                                            content.substring(0, end),
                                            "comfortType",
                                            r.get("comfort_type"),
                                            "createdAt",
                                            ActivityApi.instant(r.get("created_at")));
                                })
                        .toList();
        return ActivityApi.obj("items", items, "count", items.size());
    }

    Idempotency.Reply create(long user, String key, JsonNode body) {
        ActivityApi.fields(body, "content", "comfortType");
        var c = body.get("content");
        var t = body.get("comfortType");
        if (c == null || !c.isTextual() || t == null || !t.isTextual()) throw ActivityApi.bad();
        String content = c.textValue().strip();
        String type = t.textValue();
        if (content.isEmpty()
                || content.codePointCount(0, content.length()) > CONTENT_LIMIT
                || !COMFORT_TYPES.contains(type)) throw ActivityApi.bad();
        store.lockUser(user);
        var now = clock.instant();
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
                                    content,
                                    type,
                                    ActivityApi.ts(matures),
                                    ActivityApi.ts(now));
                    return ActivityApi.obj(
                            "concernId",
                            Long.toString(id),
                            "status",
                            "MATURING",
                            "maturesAt",
                            matures,
                            "createdAt",
                            now);
                });
    }

    @Transactional(readOnly = true)
    Object detail(long user, long id) {
        var now = clock.instant();
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
        String status = status(r, now);
        return ActivityApi.obj(
                "concernId",
                ActivityApi.sid(r.get("id")),
                "content",
                r.get("content"),
                "comfortType",
                r.get("comfort_type"),
                "createdAt",
                ActivityApi.instant(r.get("created_at")),
                "mine",
                mine,
                "status",
                status,
                "maturesAt",
                mine ? ActivityApi.instant(r.get("matures_at")) : null,
                "replyable",
                !mine && !replied && status.equals("MATURING"),
                "alreadyReplied",
                replied);
    }
}
