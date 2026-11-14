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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ConcernReplyApiMySqlIntegrationTest extends ActivityMySqlTestSupport {
    @AfterEach
    void clean() {
        db.update("DELETE FROM comfort_replies WHERE author_id IN (?,?)", user, other);
        clearFixtures("concerns");
    }

    private static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private long insert(long member, Instant maturesAt) {
        db.update(
                "INSERT INTO concerns(user_id,content,comfort_type,matures_at,created_at)"
                        + " VALUES(?,'고민','ADVICE',?,?)",
                member,
                utc(maturesAt),
                utc(Instant.parse("2026-11-09T02:00:00Z")));
        return db.queryForObject(
                "SELECT MAX(id) FROM concerns WHERE user_id=?", Long.class, member);
    }

    private long insertReply(long concern, long author, String content, Instant at) {
        db.update(
                "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,created_at)"
                        + " VALUES(?,?,'USER',?,?)",
                concern,
                author,
                content,
                utc(at));
        return db.queryForObject(
                "SELECT MAX(id) FROM comfort_replies WHERE concern_id=?", Long.class, concern);
    }

    private int replyCount(long concern) {
        return db.queryForObject(
                "SELECT count(*) FROM comfort_replies WHERE concern_id=?",
                Integer.class,
                concern);
    }

    private String reply(String content) {
        return mapper.createObjectNode().put("content", content).toString();
    }

    private JsonNode postReply(int status, long concern, String body, long member)
            throws Exception {
        return call(
                status, auth(json(post("/api/v1/concerns/" + concern + "/replies"), body), member));
    }

    private JsonNode open(int status, long concern, long member) throws Exception {
        return call(status, auth(post("/api/v1/concerns/" + concern + "/open"), member));
    }

    private JsonNode replies(int status, long concern, long member) throws Exception {
        return call(status, auth(get("/api/v1/concerns/" + concern + "/replies"), member));
    }

    private static String code(JsonNode r) {
        return r.get("code").asText();
    }

    // ---- 위로 작성 ----

    @Test
    void replyCreatesUserReply() throws Exception {
        clock.now = Instant.parse("2026-11-10T03:00:00.123456789Z");
        long id = insert(other, clock.now.plusSeconds(3600));
        var r = postReply(201, id, reply("  힘내세요  "), user);
        var d = r.get("data");
        assertEquals(3, d.size());
        assertTrue(d.get("replyId").isTextual());
        assertEquals(Long.toString(id), d.get("concernId").asText());
        assertEquals("2026-11-10T03:00:00.123456Z", d.get("createdAt").asText());
        var row =
                db.queryForMap(
                        "SELECT * FROM comfort_replies WHERE id=?", d.get("replyId").asText());
        assertEquals("USER", row.get("source_type"));
        assertEquals(user, ((Number) row.get("author_id")).longValue());
        assertEquals("힘내세요", row.get("content"));
        assertEquals(id, ((Number) row.get("concern_id")).longValue());
    }

    @Test
    void replyAcceptsBoundaryLength() throws Exception {
        long id = insert(other, clock.now.plusSeconds(3600));
        postReply(201, id, reply("가".repeat(500)), user);
    }

    @Test
    void replyRejectsInvalidInput() throws Exception {
        long id = insert(other, clock.now.plusSeconds(3600));
        String[] bad = {
            reply(""),
            reply("   "),
            reply("가".repeat(501)),
            "{}",
            "{\"content\":123}",
            "{\"content\":\"위로\",\"authorId\":1}",
            "[]"
        };
        for (String b : bad) assertEquals("COMMON_INVALID_REQUEST", code(postReply(400, id, b, user)));
        call(400, auth(post("/api/v1/concerns/" + id + "/replies"), user));
        call(400, auth(json(post("/api/v1/concerns/abc/replies"), reply("위로")), user));
        assertEquals(0, replyCount(id));
    }

    @Test
    void replyToOwnConcernIs403() throws Exception {
        long id = insert(user, clock.now.plusSeconds(3600));
        assertEquals("REPLY_OWN_CONCERN_FORBIDDEN", code(postReply(403, id, reply("위로"), user)));
        assertEquals(0, replyCount(id));
    }

    @Test
    void replyToMissingConcernIs404() throws Exception {
        assertEquals("CONCERN_NOT_FOUND", code(postReply(404, 999999999L, reply("위로"), user)));
    }

    @Test
    void secondReplyBySameUserIs409() throws Exception {
        long id = insert(other, clock.now.plusSeconds(3600));
        postReply(201, id, reply("첫 위로"), user);
        assertEquals("REPLY_ALREADY_EXISTS", code(postReply(409, id, reply("둘째 위로"), user)));
        assertEquals(1, replyCount(id));
    }

    @Test
    void replyAfterMaturityIs409() throws Exception {
        long id = insert(other, clock.now);
        assertEquals("CONCERN_REPLY_CLOSED", code(postReply(409, id, reply("위로"), user)));
        long later = insert(other, clock.now.minusSeconds(1));
        assertEquals("CONCERN_REPLY_CLOSED", code(postReply(409, later, reply("위로"), user)));
        assertEquals(0, replyCount(id) + replyCount(later));
    }

    @Test
    void concurrentRepliesBySameUserStoreOne() throws Exception {
        long id = insert(other, clock.now.plusSeconds(3600));
        var statuses = new ArrayList<Integer>();
        try (var pool = Executors.newFixedThreadPool(6)) {
            var jobs = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < 6; i++) {
                String body = reply("위로" + i);
                jobs.add(
                        () ->
                                mvc.perform(
                                                auth(
                                                        json(
                                                                post(
                                                                        "/api/v1/concerns/"
                                                                                + id
                                                                                + "/replies"),
                                                                body),
                                                        user))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
            }
            for (var f : pool.invokeAll(jobs)) statuses.add(f.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), statuses.toString());
        assertEquals(5, statuses.stream().filter(s -> s == 409).count(), statuses.toString());
        assertEquals(1, replyCount(id));
    }

    // ---- 치즈 개봉 ----

    @Test
    void openWhileMaturingIs409() throws Exception {
        long id = insert(user, clock.now.plusSeconds(1));
        assertEquals("CONCERN_NOT_MATURED", code(open(409, id, user)));
        assertNull(
                db.queryForObject("SELECT opened_at FROM concerns WHERE id=?", Object.class, id));
        assertEquals(0, replyCount(id));
    }

    @Test
    void openOthersOrMissingConcernIs404() throws Exception {
        long id = insert(other, clock.now);
        assertEquals("CONCERN_NOT_FOUND", code(open(404, id, user)));
        assertEquals("CONCERN_NOT_FOUND", code(open(404, 999999999L, user)));
        call(400, auth(post("/api/v1/concerns/abc/open"), user));
        assertNull(
                db.queryForObject("SELECT opened_at FROM concerns WHERE id=?", Object.class, id));
    }

    @Test
    void openWithRepliesAddsNoSystemReply() throws Exception {
        clock.now = Instant.parse("2026-11-10T03:00:00.123456789Z");
        long id = insert(user, Instant.parse("2026-11-10T02:00:00Z"));
        long second = insertReply(id, other, "두 번째", Instant.parse("2026-11-09T05:00:00Z"));
        var d = open(200, id, user).get("data");
        assertEquals("OPENED", d.get("status").asText());
        assertEquals("2026-11-10T03:00:00.123456Z", d.get("openedAt").asText());
        assertEquals(1, d.get("replies").size());
        assertEquals(Long.toString(second), d.at("/replies/0/replyId").asText());
        assertEquals("두 번째", d.at("/replies/0/content").asText());
        assertFalse(d.at("/replies/0/fromSystem").asBoolean());
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT count(*) FROM comfort_replies WHERE concern_id=? AND"
                                + " source_type='SYSTEM'",
                        Integer.class,
                        id));
        assertEquals(
                utc(Instant.parse("2026-11-10T03:00:00.123456Z")),
                db.queryForObject(
                        "SELECT opened_at FROM concerns WHERE id=?", LocalDateTime.class, id));
    }

    @Test
    void openWithoutRepliesAddsOneSystemReply() throws Exception {
        long id = insert(user, clock.now);
        var d = open(200, id, user).get("data");
        assertEquals("OPENED", d.get("status").asText());
        assertEquals(1, d.get("replies").size());
        var r = d.at("/replies/0");
        assertTrue(r.get("fromSystem").asBoolean());
        assertFalse(r.get("content").asText().isBlank());
        assertEquals("2026-11-10T03:00:00Z", r.get("createdAt").asText());
        var row =
                db.queryForMap(
                        "SELECT source_type, author_id FROM comfort_replies WHERE concern_id=?",
                        id);
        assertEquals("SYSTEM", row.get("source_type"));
        assertNull(row.get("author_id"));
    }

    @Test
    void reopenReturnsSameListWithoutChanges() throws Exception {
        long id = insert(user, clock.now);
        var first = open(200, id, user);
        var openedAt =
                db.queryForObject(
                        "SELECT opened_at FROM concerns WHERE id=?", LocalDateTime.class, id);
        clock.now = clock.now.plusSeconds(60);
        var second = open(200, id, user);
        assertEquals(first, second);
        assertEquals(1, replyCount(id));
        assertEquals(
                openedAt,
                db.queryForObject(
                        "SELECT opened_at FROM concerns WHERE id=?", LocalDateTime.class, id));
    }

    @Test
    void concurrentOpensStoreOneSystemReply() throws Exception {
        long id = insert(user, clock.now);
        var bodies = new ArrayList<JsonNode>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = new ArrayList<Callable<JsonNode>>();
            for (int i = 0; i < 8; i++) jobs.add(() -> open(200, id, user));
            for (var f : pool.invokeAll(jobs)) bodies.add(f.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, replyCount(id));
        for (var b : bodies) {
            assertEquals(1, b.at("/data/replies").size(), b.toString());
            assertEquals(bodies.getFirst(), b);
        }
    }

    // ---- 받은 위로 목록 ----

    @Test
    void listBeforeOpenIs409() throws Exception {
        long id = insert(user, clock.now);
        assertEquals("CONCERN_NOT_OPENED", code(replies(409, id, user)));
        long maturing = insert(user, clock.now.plusSeconds(60));
        assertEquals("CONCERN_NOT_OPENED", code(replies(409, maturing, user)));
        assertEquals(0, replyCount(id));
    }

    @Test
    void listOfOthersOrMissingConcernIs404() throws Exception {
        long id = insert(other, clock.now);
        db.update("UPDATE concerns SET opened_at=? WHERE id=?", utc(clock.now), id);
        assertEquals("CONCERN_NOT_FOUND", code(replies(404, id, user)));
        assertEquals("CONCERN_NOT_FOUND", code(replies(404, 999999999L, user)));
        call(400, auth(get("/api/v1/concerns/abc/replies"), user));
    }

    private long insertSystemReply(long concern, String content, Instant at) {
        db.update(
                "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,created_at)"
                        + " VALUES(?,NULL,'SYSTEM',?,?)",
                concern,
                content,
                utc(at));
        return db.queryForObject(
                "SELECT MAX(id) FROM comfort_replies WHERE concern_id=?", Long.class, concern);
    }

    @Test
    void listIsOrderedByTimeThenIdAndHidesAuthors() throws Exception {
        Instant t = Instant.parse("2026-11-09T05:00:00Z");
        long id = insert(user, clock.now.minusSeconds(3600));
        db.update("UPDATE concerns SET opened_at=? WHERE id=?", utc(clock.now), id);
        long late = insertReply(id, other, "나중", t.plusSeconds(60));
        long early = insertSystemReply(id, "먼저", t);
        var r = replies(200, id, user);
        var d = r.get("data");
        assertEquals(Long.toString(id), d.get("concernId").asText());
        assertEquals("OPENED", d.get("status").asText());
        assertEquals("2026-11-10T03:00:00Z", d.get("openedAt").asText());
        assertEquals(4, d.size());
        assertEquals(2, d.get("replies").size());
        assertEquals(Long.toString(early), d.at("/replies/0/replyId").asText());
        assertTrue(d.at("/replies/0/fromSystem").asBoolean());
        assertEquals(Long.toString(late), d.at("/replies/1/replyId").asText());
        assertFalse(d.at("/replies/1/fromSystem").asBoolean());
        for (var n : d.get("replies")) assertEquals(4, n.size());
        assertFalse(r.toString().contains("author"));
        assertFalse(r.toString().contains("userId"));

        db.update("UPDATE concerns SET saved_at=? WHERE id=?", utc(clock.now), id);
        assertEquals("SAVED", replies(200, id, user).at("/data/status").asText());
    }

    @Test
    void listBreaksTimeTiesById() throws Exception {
        Instant t = Instant.parse("2026-11-09T05:00:00Z");
        long id = insert(user, clock.now.minusSeconds(3600));
        db.update("UPDATE concerns SET opened_at=? WHERE id=?", utc(clock.now), id);
        long first = insertReply(id, other, "먼저 저장", t);
        long second = insertSystemReply(id, "나중 저장", t);
        var list = replies(200, id, user).at("/data/replies");
        assertEquals(Long.toString(first), list.get(0).get("replyId").asText());
        assertEquals(Long.toString(second), list.get(1).get("replyId").asText());
    }

    @Test
    void requiresAuthentication() throws Exception {
        call(
                401,
                json(post("/api/v1/concerns/1/replies"), reply("위로"))
                        .header("Authorization", "Bearer forged"));
        call(401, post("/api/v1/concerns/1/open").header("Authorization", "Bearer forged"));
        call(401, get("/api/v1/concerns/1/replies"));
    }
}
