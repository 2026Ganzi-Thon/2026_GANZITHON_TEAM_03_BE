package com.ganzithon.maeum.home;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

class HomeApiMySqlIntegrationTest extends ActivityMySqlTestSupport {
    private static final Instant CREATED = Instant.parse("2026-11-09T02:00:00Z");

    @AfterEach
    void clean() {
        clearFixtures("concerns", "emotion_records", "wool_wallets");
        db.update("DELETE FROM emotions WHERE id IN (9001,9002)");
    }

    private JsonNode home(long member) throws Exception {
        return call(200, auth(get("/api/v1/home"), member)).get("data");
    }

    private static LocalDateTime utc(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private void concern(long member, Instant matures, Instant opened, Instant saved) {
        db.update(
                "INSERT INTO concerns(user_id,content,comfort_type,matures_at,opened_at,saved_at,"
                        + "created_at) VALUES(?,?,'ADVICE',?,?,?,?)",
                member, "고민", utc(matures), utc(opened), utc(saved), utc(CREATED));
    }

    /** id 순서와 표시 순서가 반대인 감정 2개를 만들고 표시 순서대로 돌려줍니다. */
    private List<Map<String, Object>> emotions() {
        db.update("DELETE FROM emotions WHERE id IN (9001,9002)");
        db.update(
                "INSERT INTO emotions(id,code,name,display_order,active) VALUES"
                        + " (9001,'HOME_TIRED','지침',2,true),(9002,'HOME_CALM','평온',1,true)");
        return db.queryForList(
                "SELECT id,code,name FROM emotions WHERE id IN (9001,9002) ORDER BY display_order,id");
    }

    private long record(long member, String date, List<Map<String, Object>> picks) {
        db.update(
                "INSERT INTO emotion_records(user_id,record_date,created_at,updated_at)"
                        + " VALUES(?,?,?,?)",
                member, date, utc(CREATED), utc(CREATED));
        long id = db.queryForObject(
                "SELECT id FROM emotion_records WHERE user_id=? AND record_date=?",
                Long.class, member, date);
        // 정렬 확인을 위해 역순으로 넣습니다.
        for (int i = picks.size() - 1; i >= 0; i--)
            db.update(
                    "INSERT INTO emotion_record_emotions(emotion_record_id,emotion_id) VALUES(?,?)",
                    id, picks.get(i).get("id"));
        return id;
    }

    @Test
    void newMemberGetsEmptySummary() throws Exception {
        var d = home(user);
        assertTrue(d.get("nickname").isNull());
        assertEquals(0, d.get("woolBalance").asInt());
        assertEquals("2026-11-10", d.get("today").asText());
        var e = d.get("todayEmotion");
        assertFalse(e.get("recorded").asBoolean());
        assertTrue(e.get("recordId").isNull());
        assertEquals(0, e.get("emotions").size());
        assertEquals(0, d.get("maturedConcernCount").asInt());
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM wool_wallets WHERE user_id=?", Integer.class, user));
    }

    @Test
    void responseIsNoStore() throws Exception {
        var r = mvc.perform(auth(get("/api/v1/home"), user)).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        assertTrue(r.getHeader("Cache-Control").contains("no-store"));
    }

    @Test
    void returnsNicknameWalletAndTodayEmotionsSorted() throws Exception {
        db.update("UPDATE users SET nickname=? WHERE id=?", "온기" + user, user);
        db.update("INSERT INTO wool_wallets(user_id,balance,updated_at) VALUES(?,12,?)",
                user, utc(CREATED));
        var picks = emotions();
        long id = record(user, "2026-11-10", picks);
        var d = home(user);
        assertEquals("온기" + user, d.get("nickname").asText());
        assertEquals(12, d.get("woolBalance").asInt());
        var e = d.get("todayEmotion");
        assertTrue(e.get("recorded").asBoolean());
        assertEquals(Long.toString(id), e.get("recordId").asText());
        assertEquals(2, e.get("emotions").size());
        for (int i = 0; i < 2; i++) {
            var item = e.get("emotions").get(i);
            assertEquals(picks.get(i).get("id").toString(), item.get("emotionId").asText());
            assertEquals(picks.get(i).get("code"), item.get("code").asText());
            assertEquals(picks.get(i).get("name"), item.get("name").asText());
            assertEquals(3, item.size());
        }
    }

    @Test
    void yesterdayRecordIsNotToday() throws Exception {
        record(user, "2026-11-09", emotions());
        assertFalse(home(user).get("todayEmotion").get("recorded").asBoolean());
    }

    @Test
    void todayFollowsSeoulDate() throws Exception {
        // 토큰 유효 시간(1시간) 때문에 날짜 경계 직전 시각에 새로 로그인합니다.
        clock.now = Instant.parse("2026-11-10T14:30:00Z");
        long member = login();
        try {
            seoulBoundary(member);
        } finally {
            db.update("DELETE FROM emotion_records WHERE user_id=?", member);
            db.update(
                    "DELETE FROM refresh_tokens WHERE session_id IN (SELECT id FROM auth_sessions"
                            + " WHERE user_id=?)",
                    member);
            db.update("DELETE FROM auth_sessions WHERE user_id=?", member);
            db.update("DELETE FROM local_credentials WHERE user_id=?", member);
            db.update("DELETE FROM users WHERE id=?", member);
        }
    }

    private void seoulBoundary(long member) throws Exception {
        record(member, "2026-11-10", emotions());
        clock.now = Instant.parse("2026-11-10T14:59:59Z");
        var before = home(member);
        assertEquals("2026-11-10", before.get("today").asText());
        assertTrue(before.get("todayEmotion").get("recorded").asBoolean());
        clock.now = Instant.parse("2026-11-10T15:00:00Z");
        var after = home(member);
        assertEquals("2026-11-11", after.get("today").asText());
        assertFalse(after.get("todayEmotion").get("recorded").asBoolean());
    }

    @Test
    void maturedConcernCountExcludesMaturingOpenedSavedAndOthers() throws Exception {
        concern(user, CREATED, null, null); // 숙성 완료, 미개봉
        concern(user, clock.now, null, null); // 경계: 정확히 지금
        concern(user, clock.now.plusSeconds(1), null, null); // 숙성 중
        concern(user, CREATED, CREATED.plusSeconds(10), null); // 개봉
        concern(user, CREATED, CREATED.plusSeconds(10), CREATED.plusSeconds(20)); // 보관
        concern(other, CREATED, null, null); // 타인
        assertEquals(2, home(user).get("maturedConcernCount").asInt());
        assertEquals(1, home(other).get("maturedConcernCount").asInt());
    }

    @Test
    void doesNotMixOtherMemberData() throws Exception {
        db.update("UPDATE users SET nickname=? WHERE id=?", "남" + other, other);
        db.update("INSERT INTO wool_wallets(user_id,balance,updated_at) VALUES(?,99,?)",
                other, utc(CREATED));
        record(other, "2026-11-10", emotions());
        var d = home(user);
        assertTrue(d.get("nickname").isNull());
        assertEquals(0, d.get("woolBalance").asInt());
        assertFalse(d.get("todayEmotion").get("recorded").asBoolean());
    }

    @Test
    void requiresAuthentication() throws Exception {
        var r = call(401, get("/api/v1/home"));
        assertEquals(401, mvc.perform(get("/api/v1/home")).andReturn().getResponse().getStatus());
        assertNotNull(r);
    }
}
