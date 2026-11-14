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

class ConcernSavedApiMySqlIntegrationTest extends ActivityMySqlTestSupport {
    private static final Instant CREATED = Instant.parse("2026-11-09T02:00:00Z");

    @AfterEach
    void clean() {
        db.update("DELETE FROM comfort_replies WHERE author_id IN (?,?)", user, other);
        clearFixtures("concerns");
    }

    private static LocalDateTime utc(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private long insert(long member, String content, Instant created, Instant matures,
            Instant opened, Instant saved) {
        db.update(
                "INSERT INTO concerns(user_id,content,comfort_type,matures_at,opened_at,saved_at,"
                        + "created_at) VALUES(?,?,'ADVICE',?,?,?,?)",
                member, content, utc(matures), utc(opened), utc(saved), utc(created));
        return db.queryForObject("SELECT MAX(id) FROM concerns WHERE user_id=?", Long.class, member);
    }

    private long maturing(long member) {
        return insert(member, "고민", CREATED, clock.now.plusSeconds(3600), null, null);
    }

    private long opened(long member) {
        return insert(member, "고민", CREATED, CREATED.plusSeconds(10), CREATED.plusSeconds(20), null);
    }

    private long saved(long member, Instant savedAt) {
        return insert(member, "고민", CREATED, CREATED.plusSeconds(10), CREATED.plusSeconds(20), savedAt);
    }

    private void reply(long concern, Long author, String type) {
        db.update(
                "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,created_at)"
                        + " VALUES(?,?,?,'위로',?)",
                concern, author, type, utc(CREATED.plusSeconds(30)));
    }

    private JsonNode mine(int status, String query, long member) throws Exception {
        return call(status, auth(get("/api/v1/users/me/concerns" + query), member));
    }

    private JsonNode box(int status, String query, long member) throws Exception {
        return call(status, auth(get("/api/v1/users/me/saved-concerns" + query), member));
    }

    private JsonNode doPatch(int status, long id, String action, long member) throws Exception {
        return call(status, auth(patch("/api/v1/concerns/" + id + "/" + action), member));
    }

    private static String code(JsonNode r) {
        return r.get("code").asText();
    }

    private static String ids(JsonNode items) {
        var sb = new StringBuilder();
        for (var i : items) sb.append(i.get("concernId").asText()).append(',');
        return sb.toString();
    }

    // ---- 내 치즈 목록 ----

    @Test
    void myListSortsNewestFirstWithIdTieBreak() throws Exception {
        long a = insert(user, "a", CREATED, CREATED, null, null);
        long b = insert(user, "b", CREATED.plusSeconds(5), CREATED, null, null);
        long c = insert(user, "c", CREATED, CREATED, null, null);
        var d = mine(200, "", user).get("data");
        assertEquals(b + "," + c + "," + a + ",", ids(d.get("items")));
        assertEquals(0, d.get("page").asInt());
        assertEquals(20, d.get("size").asInt());
        assertFalse(d.get("hasNext").asBoolean());
    }

    @Test
    void myListComputesFourStatusesFromServerClock() throws Exception {
        long maturing = insert(user, "m", CREATED.plusSeconds(1), clock.now.plusSeconds(60), null, null);
        long matured = insert(user, "m", CREATED.plusSeconds(2), clock.now.plusSeconds(120), null, null);
        long opened = insert(user, "o", CREATED.plusSeconds(3), CREATED, CREATED.plusSeconds(5), null);
        long saved = insert(user, "s", CREATED.plusSeconds(4), CREATED, CREATED.plusSeconds(5), CREATED.plusSeconds(6));
        var first = mine(200, "", user).get("data").get("items");
        assertEquals("SAVED", first.get(0).get("status").asText());
        assertEquals("OPENED", first.get(1).get("status").asText());
        assertEquals("MATURING", first.get(2).get("status").asText());
        assertEquals("MATURING", first.get(3).get("status").asText());
        assertTrue(first.get(2).get("openedAt").isNull());
        assertNotNull(first.get(1).get("openedAt").asText());
        clock.now = clock.now.plusSeconds(90);
        var second = mine(200, "", user).get("data").get("items");
        assertEquals("MATURING", second.get(2).get("status").asText());
        assertEquals("MATURED", second.get(3).get("status").asText());
        assertEquals(Long.toString(matured), second.get(2).get("concernId").asText());
        assertEquals(Long.toString(maturing), second.get(3).get("concernId").asText());
        assertNotEquals(0, saved + opened);
    }

    @Test
    void myListItemFieldsExcludeReplyCountAndOthers() throws Exception {
        insert(other, "남의 고민", CREATED, CREATED, null, null);
        long id = insert(user, "가".repeat(150), CREATED, CREATED, null, null);
        reply(id, other, "USER");
        var d = mine(200, "", user).get("data");
        assertEquals(1, d.get("items").size());
        var item = d.get("items").get(0);
        assertEquals(7, item.size());
        assertEquals("가".repeat(100), item.get("preview").asText());
        assertEquals("ADVICE", item.get("comfortType").asText());
        assertFalse(item.has("replyCount"));
        assertFalse(item.has("userId"));
        assertEquals("2026-11-09T02:00:00Z", item.get("createdAt").asText());
    }

    @Test
    void myListPagesAndRejectsBadParams() throws Exception {
        for (int i = 0; i < 3; i++) insert(user, "x", CREATED.plusSeconds(i), CREATED, null, null);
        var p0 = mine(200, "?page=0&size=2", user).get("data");
        assertEquals(2, p0.get("items").size());
        assertTrue(p0.get("hasNext").asBoolean());
        var p1 = mine(200, "?page=1&size=2", user).get("data");
        assertEquals(1, p1.get("items").size());
        assertFalse(p1.get("hasNext").asBoolean());
        for (String q : new String[] {"?page=-1", "?size=0", "?size=101", "?page=abc"})
            assertEquals("COMMON_INVALID_REQUEST", code(mine(400, q, user)));
    }

    // ---- 보관 ----

    @Test
    void saveSetsSavedAtOnceAndRepeatKeepsIt() throws Exception {
        long id = opened(user);
        clock.now = Instant.parse("2026-11-10T03:05:00.123456789Z");
        var d = doPatch(200, id, "save", user).get("data");
        assertEquals("SAVED", d.get("status").asText());
        assertEquals("2026-11-10T03:05:00.123456Z", d.get("savedAt").asText());
        assertEquals(Long.toString(id), d.get("concernId").asText());
        clock.now = clock.now.plusSeconds(120);
        var again = doPatch(200, id, "save", user).get("data");
        assertEquals("2026-11-10T03:05:00.123456Z", again.get("savedAt").asText());
    }

    @Test
    void saveBeforeOpenIs409() throws Exception {
        long id = maturing(user);
        assertEquals("CONCERN_NOT_OPENED", code(doPatch(409, id, "save", user)));
        assertNull(db.queryForObject("SELECT saved_at FROM concerns WHERE id=?", Object.class, id));
    }

    @Test
    void saveOthersOrMissingIs404() throws Exception {
        long id = opened(other);
        assertEquals("CONCERN_NOT_FOUND", code(doPatch(404, id, "save", user)));
        assertEquals("CONCERN_NOT_FOUND", code(doPatch(404, 999999999L, "save", user)));
        assertNull(db.queryForObject("SELECT saved_at FROM concerns WHERE id=?", Object.class, id));
    }

    @Test
    void patchRejectsBodyAndBadId() throws Exception {
        long id = opened(user);
        call(400, auth(json(patch("/api/v1/concerns/" + id + "/save"), "{}"), user));
        call(400, auth(json(patch("/api/v1/concerns/" + id + "/unsave"), "{}"), user));
        call(400, auth(patch("/api/v1/concerns/abc/save"), user));
        call(400, auth(json(delete("/api/v1/concerns/" + id), "{}"), user));
    }

    // ---- 보관 취소 ----

    @Test
    void unsaveRestoresOpenedAndRepeatIsSame() throws Exception {
        long id = saved(user, CREATED.plusSeconds(40));
        for (int i = 0; i < 2; i++) {
            var d = doPatch(200, id, "unsave", user).get("data");
            assertEquals("OPENED", d.get("status").asText());
            assertTrue(d.get("savedAt").isNull());
            assertEquals(3, d.size());
        }
        assertEquals(0, box(200, "", user).get("data").get("items").size());
    }

    @Test
    void unsaveUnopenedReturnsComputedStatus() throws Exception {
        long id = insert(user, "고민", CREATED, clock.now.plusSeconds(60), null, null);
        assertEquals("MATURING", doPatch(200, id, "unsave", user).get("data").get("status").asText());
        clock.now = clock.now.plusSeconds(120);
        assertEquals("MATURED", doPatch(200, id, "unsave", user).get("data").get("status").asText());
    }

    @Test
    void unsaveOfNeverSavedConcernKeepsRowAsIs() throws Exception {
        long maturing = maturing(user);
        long opened = opened(user);
        var d = doPatch(200, maturing, "unsave", user).get("data");
        assertEquals("MATURING", d.get("status").asText());
        assertTrue(d.get("savedAt").isNull());
        d = doPatch(200, opened, "unsave", user).get("data");
        assertEquals("OPENED", d.get("status").asText());
        assertTrue(d.get("savedAt").isNull());
        for (long id : new long[] {maturing, opened}) {
            assertNull(
                    db.queryForObject("SELECT saved_at FROM concerns WHERE id=?", Object.class, id));
        }
        assertNull(
                db.queryForObject("SELECT opened_at FROM concerns WHERE id=?", Object.class, maturing));
        assertNotNull(
                db.queryForObject("SELECT opened_at FROM concerns WHERE id=?", Object.class, opened));
    }

    @Test
    void unsaveOthersOrMissingIs404AndKeepsSaved() throws Exception {
        long id = saved(other, CREATED.plusSeconds(40));
        assertEquals("CONCERN_NOT_FOUND", code(doPatch(404, id, "unsave", user)));
        assertEquals("CONCERN_NOT_FOUND", code(doPatch(404, 999999999L, "unsave", user)));
        assertNotNull(
                db.queryForObject("SELECT saved_at FROM concerns WHERE id=?", Object.class, id));
    }

    // ---- 보관함 ----

    @Test
    void boxSortsBySavedAtDescThenIdAndExcludesOthers() throws Exception {
        long a = saved(user, CREATED.plusSeconds(40));
        long b = saved(user, CREATED.plusSeconds(50));
        long c = saved(user, CREATED.plusSeconds(40));
        opened(user);
        saved(other, CREATED.plusSeconds(60));
        var d = box(200, "", user).get("data");
        assertEquals(b + "," + c + "," + a + ",", ids(d.get("items")));
        var item = d.get("items").get(0);
        assertEquals(5, item.size());
        assertEquals("2026-11-09T02:00:50Z", item.get("savedAt").asText());
        assertEquals(20, d.get("size").asInt());
        assertFalse(d.get("hasNext").asBoolean());
    }

    @Test
    void boxReplyCountIsExactIncludingSystemReply() throws Exception {
        long none = saved(user, CREATED.plusSeconds(40));
        long many = saved(user, CREATED.plusSeconds(41));
        long system = saved(user, CREATED.plusSeconds(42));
        reply(many, other, "USER");
        reply(many, user, "USER");
        reply(many, null, "SYSTEM");
        reply(system, null, "SYSTEM");
        var items = box(200, "", user).get("data").get("items");
        assertEquals(1, items.get(0).get("replyCount").asInt());
        assertEquals(3, items.get(1).get("replyCount").asInt());
        assertEquals(0, items.get(2).get("replyCount").asInt());
        assertEquals(Long.toString(none), items.get(2).get("concernId").asText());
    }

    @Test
    void boxPagesAndRejectsBadParams() throws Exception {
        for (int i = 0; i < 3; i++) saved(user, CREATED.plusSeconds(40 + i));
        var p0 = box(200, "?page=0&size=2", user).get("data");
        assertEquals(2, p0.get("items").size());
        assertTrue(p0.get("hasNext").asBoolean());
        assertEquals(1, box(200, "?page=1&size=2", user).get("data").get("items").size());
        assertEquals("COMMON_INVALID_REQUEST", code(box(400, "?size=0", user)));
        assertEquals("COMMON_INVALID_REQUEST", code(box(400, "?page=-1", user)));
    }

    // ---- 삭제 ----

    @Test
    void deleteRemovesConcernAndRepliesThen404() throws Exception {
        long id = saved(user, CREATED.plusSeconds(40));
        reply(id, other, "USER");
        reply(id, null, "SYSTEM");
        var res = mvc.perform(auth(delete("/api/v1/concerns/" + id), user)).andReturn().getResponse();
        assertEquals(204, res.getStatus());
        assertEquals("", res.getContentAsString());
        assertEquals(0, db.queryForObject("SELECT count(*) FROM concerns WHERE id=?", Integer.class, id));
        assertEquals(0, db.queryForObject(
                "SELECT count(*) FROM comfort_replies WHERE concern_id=?", Integer.class, id));
        assertEquals("CONCERN_NOT_FOUND", code(call(404, auth(delete("/api/v1/concerns/" + id), user))));
    }

    @Test
    void deleteMaturingConcernWorks() throws Exception {
        long id = maturing(user);
        call(204, auth(delete("/api/v1/concerns/" + id), user));
    }

    @Test
    void deleteOthersConcernIs404AndKeepsIt() throws Exception {
        long id = opened(other);
        reply(id, user, "USER");
        assertEquals("CONCERN_NOT_FOUND", code(call(404, auth(delete("/api/v1/concerns/" + id), user))));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM concerns WHERE id=?", Integer.class, id));
        assertEquals(1, db.queryForObject(
                "SELECT count(*) FROM comfort_replies WHERE concern_id=?", Integer.class, id));
    }

    @Test
    void requiresAuthentication() throws Exception {
        call(401, get("/api/v1/users/me/concerns"));
        call(401, get("/api/v1/users/me/saved-concerns"));
        String forged = "Bearer forged";
        call(401, patch("/api/v1/concerns/1/save").header("Authorization", forged));
        call(401, patch("/api/v1/concerns/1/unsave").header("Authorization", forged));
        call(401, delete("/api/v1/concerns/1").header("Authorization", forged));
    }
}
