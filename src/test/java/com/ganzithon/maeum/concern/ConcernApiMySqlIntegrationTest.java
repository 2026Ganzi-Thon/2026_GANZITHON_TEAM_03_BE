package com.ganzithon.maeum.concern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ConcernApiMySqlIntegrationTest extends ActivityMySqlTestSupport {
    @AfterEach
    void clean() {
        db.update("DELETE FROM comfort_replies WHERE author_id IN (?,?)", user, other);
        clearFixtures("concerns", "api_idempotency_requests");
    }

    private String body(String content, String type) {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode()
                .put("content", content)
                .put("comfortType", type)
                .toString();
    }

    private JsonNode create(long member, String content, String type) throws Exception {
        return call(
                201,
                auth(
                        json(post("/api/v1/concerns"), body(content, type))
                                .header("Idempotency-Key", key()),
                        member));
    }

    private long insert(long member, String content, Instant maturesAt) {
        return insertAt(member, content, maturesAt, Instant.parse("2026-11-10T02:00:00Z"));
    }

    private long insertAt(long member, String content, Instant maturesAt, Instant createdAt) {
        db.update(
                "INSERT INTO concerns(user_id,content,comfort_type,matures_at,created_at)"
                        + " VALUES(?,?,'ADVICE',?,?)",
                member,
                content,
                LocalDateTime.ofInstant(maturesAt, ZoneOffset.UTC),
                LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return db.queryForObject("SELECT MAX(id) FROM concerns WHERE user_id=?", Long.class, member);
    }

    @Test
    void createReturns201WithMaturingStatus() throws Exception {
        var r = create(user, "  취업 준비가 길어져요  ", "EMPATHY");
        assertEquals("MATURING", r.at("/data/status").asText());
        assertEquals("2026-11-10T03:00:00Z", r.at("/data/createdAt").asText());
        assertEquals("2026-11-11T03:00:00Z", r.at("/data/maturesAt").asText());
        assertTrue(r.at("/data/concernId").isTextual());
        assertEquals(4, r.get("data").size());
        assertEquals(
                "취업 준비가 길어져요",
                db.queryForObject(
                        "SELECT content FROM concerns WHERE id=?",
                        String.class,
                        r.at("/data/concernId").asText()));
    }

    @Test
    void createResponseTimesMatchStoredTimes() throws Exception {
        clock.now = Instant.parse("2026-11-10T03:00:00.123456789Z");
        var created = create(user, "고민", "EMPATHY").get("data");
        var detail =
                call(200, auth(get("/api/v1/concerns/" + created.get("concernId").asText()), user))
                        .get("data");
        assertEquals("2026-11-10T03:00:00.123456Z", created.get("createdAt").asText());
        assertEquals(detail.get("createdAt").asText(), created.get("createdAt").asText());
        assertEquals(detail.get("maturesAt").asText(), created.get("maturesAt").asText());
    }

    @Test
    void createRejectsInvalidInput() throws Exception {
        String k = key();
        String[] bad = {
            body("", "EMPATHY"),
            body("   ", "EMPATHY"),
            body("가".repeat(1001), "EMPATHY"),
            body("고민", "UNKNOWN"),
            "{\"content\":\"고민\"}",
            "{\"content\":123,\"comfortType\":\"EMPATHY\"}",
            "{\"content\":\"고민\",\"comfortType\":\"EMPATHY\",\"userId\":1}",
            "[]"
        };
        for (String b : bad)
            call(
                    400,
                    auth(
                            json(post("/api/v1/concerns"), b).header("Idempotency-Key", k),
                            user));
        call(400, auth(json(post("/api/v1/concerns"), body("고민", "EMPATHY")), user));
        call(
                400,
                auth(
                        json(post("/api/v1/concerns"), body("고민", "EMPATHY"))
                                .header("Idempotency-Key", "not-a-uuid"),
                        user));
        call(201, auth(json(post("/api/v1/concerns"), body("가".repeat(1000), "ADVICE")).header("Idempotency-Key", key()), user));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM concerns WHERE user_id=?", Integer.class, user));
    }

    @Test
    void sameKeySameRequestReplaysWithoutDuplicate() throws Exception {
        String k = key(), b = body("고민", "EXPERIENCE");
        var first = call(201, auth(json(post("/api/v1/concerns"), b).header("Idempotency-Key", k), user));
        clock.now = clock.now.plusSeconds(60);
        var second = call(201, auth(json(post("/api/v1/concerns"), b).header("Idempotency-Key", k), user));
        assertEquals(first, second);
        assertEquals(1, db.queryForObject("SELECT count(*) FROM concerns WHERE user_id=?", Integer.class, user));
    }

    @Test
    void sameKeyDifferentRequestConflicts() throws Exception {
        String k = key();
        call(201, auth(json(post("/api/v1/concerns"), body("고민", "EMPATHY")).header("Idempotency-Key", k), user));
        assertEquals(
                "COMMON_IDEMPOTENCY_CONFLICT",
                call(409, auth(json(post("/api/v1/concerns"), body("다른 고민", "EMPATHY")).header("Idempotency-Key", k), user))
                        .get("code")
                        .asText());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM concerns WHERE user_id=?", Integer.class, user));
    }

    @Test
    void concurrentRetriesCreateOnlyOne() throws Exception {
        String k = key(), b = body("고민", "EMPATHY");
        try (var pool = Executors.newFixedThreadPool(6)) {
            var jobs = new ArrayList<Callable<JsonNode>>();
            for (int i = 0; i < 6; i++)
                jobs.add(() -> call(201, auth(json(post("/api/v1/concerns"), b).header("Idempotency-Key", k), user)));
            var ids = new HashSet<String>();
            for (var f : pool.invokeAll(jobs))
                ids.add(f.get(20, TimeUnit.SECONDS).at("/data/concernId").asText());
            assertEquals(1, ids.size());
        }
        assertEquals(1, db.queryForObject("SELECT count(*) FROM concerns WHERE user_id=?", Integer.class, user));
    }

    @Test
    void listExcludesMineMaturedAndRepliedAndHidesAuthor() throws Exception {
        Instant future = clock.now.plusSeconds(3600);
        long visible = insert(other, "가".repeat(150), future);
        insert(user, "내 고민", future);
        insert(other, "숙성 끝", clock.now);
        insert(other, "숙성 끝2", clock.now.minusSeconds(1));
        long replied = insert(other, "이미 위로함", future);
        db.update(
                "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,created_at)"
                        + " VALUES(?,?,'USER','위로',?)",
                replied,
                user,
                LocalDateTime.ofInstant(clock.now, ZoneOffset.UTC));
        var r = call(200, auth(get("/api/v1/concerns"), user));
        assertEquals(1, r.at("/data/count").asInt());
        var item = r.at("/data/items/0");
        assertEquals(Long.toString(visible), item.get("concernId").asText());
        assertEquals("가".repeat(100), item.get("preview").asText());
        assertEquals("ADVICE", item.get("comfortType").asText());
        assertEquals("2026-11-10T02:00:00Z", item.get("createdAt").asText());
        assertEquals(4, item.size());
        assertFalse(r.toString().contains("userId"));
        assertEquals(
                1,
                call(200, auth(get("/api/v1/concerns"), other)).at("/data/count").asInt());
    }

    @Test
    void listIsEmptyArrayAndCapsAt100() throws Exception {
        assertEquals(0, call(200, auth(get("/api/v1/concerns"), user)).at("/data/items").size());
        Instant future = clock.now.plusSeconds(3600);
        for (int i = 0; i < 105; i++) insert(other, "고민" + i, future);
        var r = call(200, auth(get("/api/v1/concerns"), user));
        assertEquals(100, r.at("/data/count").asInt());
        assertEquals(100, r.at("/data/items").size());
        var ids = new HashSet<String>();
        r.at("/data/items").forEach(n -> ids.add(n.get("concernId").asText()));
        assertEquals(100, ids.size());
    }

    @Test
    void detailOfOthersConcernHidesMaturesAtAndAuthor() throws Exception {
        long id = insert(other, "남의 고민", clock.now.plusSeconds(3600));
        var r = call(200, auth(get("/api/v1/concerns/" + id), user));
        var d = r.get("data");
        assertEquals("남의 고민", d.get("content").asText());
        assertEquals("ADVICE", d.get("comfortType").asText());
        assertFalse(d.get("mine").asBoolean());
        assertEquals("MATURING", d.get("status").asText());
        assertTrue(d.get("maturesAt").isNull());
        assertTrue(d.get("replyable").asBoolean());
        assertFalse(d.get("alreadyReplied").asBoolean());
        assertEquals(9, d.size());
        assertFalse(r.toString().contains("userId"));

        db.update(
                "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,created_at)"
                        + " VALUES(?,?,'USER','위로',?)",
                id,
                user,
                LocalDateTime.ofInstant(clock.now, ZoneOffset.UTC));
        var after = call(200, auth(get("/api/v1/concerns/" + id), user)).get("data");
        assertTrue(after.get("alreadyReplied").asBoolean());
        assertFalse(after.get("replyable").asBoolean());
    }

    @Test
    void detailOfMyConcernShowsMaturesAtAndStatuses() throws Exception {
        Instant future = clock.now.plusSeconds(3600);
        long id = insert(user, "내 고민", future);
        var d = call(200, auth(get("/api/v1/concerns/" + id), user)).get("data");
        assertTrue(d.get("mine").asBoolean());
        assertEquals("2026-11-10T04:00:00Z", d.get("maturesAt").asText());
        assertFalse(d.get("replyable").asBoolean());
        assertFalse(d.get("alreadyReplied").asBoolean());

        long matured = insert(user, "숙성 끝", clock.now);
        assertEquals("MATURED", call(200, auth(get("/api/v1/concerns/" + matured), user)).at("/data/status").asText());
        db.update("UPDATE concerns SET opened_at=? WHERE id=?", LocalDateTime.ofInstant(clock.now, ZoneOffset.UTC), matured);
        assertEquals("OPENED", call(200, auth(get("/api/v1/concerns/" + matured), user)).at("/data/status").asText());
        db.update("UPDATE concerns SET saved_at=? WHERE id=?", LocalDateTime.ofInstant(clock.now, ZoneOffset.UTC), matured);
        assertEquals("SAVED", call(200, auth(get("/api/v1/concerns/" + matured), user)).at("/data/status").asText());

        long othersMatured = insert(other, "남의 숙성 끝", clock.now);
        var o = call(200, auth(get("/api/v1/concerns/" + othersMatured), user)).get("data");
        assertEquals("MATURED", o.get("status").asText());
        assertFalse(o.get("replyable").asBoolean());
        assertTrue(o.get("maturesAt").isNull());
    }

    @Test
    void detailReturns404ForMissingAndRejectsBadId() throws Exception {
        assertEquals("CONCERN_NOT_FOUND", call(404, auth(get("/api/v1/concerns/999999999"), user)).get("code").asText());
        call(400, auth(get("/api/v1/concerns/abc"), user));
        call(400, auth(get("/api/v1/concerns/0"), user));
    }

    @Test
    void requiresAuthentication() throws Exception {
        call(401, get("/api/v1/concerns"));
        call(401, get("/api/v1/concerns/1"));
        call(
                401,
                json(post("/api/v1/concerns"), body("고민", "EMPATHY"))
                        .header("Idempotency-Key", key())
                        .header("Authorization", "Bearer forged"));
    }
}
