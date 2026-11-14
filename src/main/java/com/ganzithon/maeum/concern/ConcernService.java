package com.ganzithon.maeum.concern;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;
import com.ganzithon.maeum.global.activity.Idempotency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
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
    private static final int REPLY_LIMIT = 500;
    private static final List<String> DEFAULT_COMFORTS =
            List.of(
                    "여기까지 온 것만으로도 충분히 잘하고 있어요.",
                    "혼자 끌어안고 있던 마음을 꺼내 놓은 것만으로도 큰 용기였어요.",
                    "지금 느끼는 감정은 자연스러운 것이니 스스로를 너무 다그치지 않아도 괜찮아요.",
                    "힘든 시간도 천천히 지나갈 거예요. 오늘은 조금 쉬어가도 좋아요.");
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
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
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

    Object reply(long user, long id, JsonNode body) {
        ActivityApi.fields(body, "content");
        var c = body.get("content");
        if (c == null || !c.isTextual()) throw ActivityApi.bad();
        String content = c.textValue().strip();
        if (content.isEmpty() || content.codePointCount(0, content.length()) > REPLY_LIMIT)
            throw ActivityApi.bad();
        var concern =
                store.required(
                        "SELECT user_id,matures_at FROM concerns WHERE id=?",
                        "CONCERN_NOT_FOUND",
                        id);
        if (((Number) concern.get("user_id")).longValue() == user)
            throw ActivityApi.problem(403, "REPLY_OWN_CONCERN_FORBIDDEN");
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (!now.isBefore(ActivityApi.instant(concern.get("matures_at"))))
            throw ActivityApi.problem(409, "CONCERN_REPLY_CLOSED");
        if (store.one(
                        "SELECT id FROM comfort_replies WHERE concern_id=? AND author_id=?",
                        id,
                        user)
                != null) throw ActivityApi.problem(409, "REPLY_ALREADY_EXISTS");
        long replyId;
        try {
            replyId =
                    store.insert(
                            "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,"
                                + "created_at) VALUES(?,?,'USER',?,?)",
                            id,
                            user,
                            content,
                            ActivityApi.ts(now));
        } catch (DuplicateKeyException e) {
            throw ActivityApi.problem(409, "REPLY_ALREADY_EXISTS");
        }
        return ActivityApi.obj(
                "replyId", Long.toString(replyId), "concernId", Long.toString(id), "createdAt", now);
    }

    Object open(long user, long id) {
        // 개봉 UPDATE를 첫 쿼리로 실행해 동시 요청이 행 잠금에서 직렬화되고, 이후 조회는 커밋된 결과를 보게 한다.
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        int opened =
                store.update(
                        "UPDATE concerns SET opened_at=? WHERE id=? AND user_id=? AND opened_at IS"
                            + " NULL AND matures_at<=?",
                        ActivityApi.ts(now),
                        id,
                        user,
                        ActivityApi.ts(now));
        if (opened == 1
                && store.one("SELECT id FROM comfort_replies WHERE concern_id=? LIMIT 1", id)
                        == null) {
            store.insert(
                    "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,"
                        + "created_at) VALUES(?,NULL,'SYSTEM',?,?)",
                    id,
                    DEFAULT_COMFORTS.get(
                            java.util.concurrent.ThreadLocalRandom.current()
                                    .nextInt(DEFAULT_COMFORTS.size())),
                    ActivityApi.ts(now));
        }
        return received(user, id, "CONCERN_NOT_MATURED");
    }

    @Transactional(readOnly = true)
    Object replies(long user, long id) {
        return received(user, id, "CONCERN_NOT_OPENED");
    }

    private Object received(long user, long id, String notOpenedCode) {
        var r =
                store.required(
                        "SELECT id,opened_at,saved_at FROM concerns WHERE id=? AND user_id=?",
                        "CONCERN_NOT_FOUND",
                        id,
                        user);
        if (r.get("opened_at") == null) throw ActivityApi.problem(409, notOpenedCode);
        var replies =
                store
                        .list(
                                "SELECT id,content,source_type,created_at FROM comfort_replies"
                                    + " WHERE concern_id=? ORDER BY created_at,id",
                                id)
                        .stream()
                        .map(
                                x ->
                                        ActivityApi.obj(
                                                "replyId",
                                                ActivityApi.sid(x.get("id")),
                                                "content",
                                                x.get("content"),
                                                "fromSystem",
                                                "SYSTEM".equals(x.get("source_type")),
                                                "createdAt",
                                                ActivityApi.instant(x.get("created_at"))))
                        .toList();
        return ActivityApi.obj(
                "concernId",
                ActivityApi.sid(r.get("id")),
                "status",
                r.get("saved_at") != null ? "SAVED" : "OPENED",
                "openedAt",
                ActivityApi.instant(r.get("opened_at")),
                "replies",
                replies);
    }
}
