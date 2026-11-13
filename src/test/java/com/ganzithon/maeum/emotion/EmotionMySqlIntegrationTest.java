package com.ganzithon.maeum.emotion;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmotionMySqlIntegrationTest extends ActivityMySqlTestSupport {

    @BeforeEach
    void masterData() {
        db.update(
                "INSERT INTO emotions(id,code,name,display_order,active)"
                    + " VALUES(1,'JOY','기쁨',1,true),(2,'CALM','평온',2,true),(3,'OLD','과거',3,false)");
    }

    @AfterEach
    void clean() {
        clearFixtures("emotion_records");
        db.update("DELETE FROM emotions WHERE id IN (1,2,3)");
    }

    private String createRecord() throws Exception {
        return call(
                        201,
                        auth(
                                json(
                                        post("/api/v1/emotion-records"),
                                        "{\"emotionIds\":[\"1\",\"2\"],\"memo\":\"hello\"}"),
                                user))
                .at("/data/recordId")
                .asText();
    }

    @Test
    void validatesServiceAccessToken() throws Exception {
        assertEquals(
                "AUTH_ACCESS_TOKEN_INVALID",
                call(401, get("/api/v1/emotions")).get("code").asText());
        call(401, get("/api/v1/emotions").header("Authorization", "Bearer forged"));
        clock.now = clock.now.plusSeconds(3600);
        assertEquals(
                "AUTH_ACCESS_TOKEN_EXPIRED",
                call(401, auth(get("/api/v1/emotions"), user)).get("code").asText());
    }

    @Test
    void emotionCrudAndPrivacy() throws Exception {
        assertEquals(2, call(200, auth(get("/api/v1/emotions"), user)).at("/data/items").size());
        String id = createRecord();
        assertEquals(
                1, call(200, auth(get("/api/v1/emotion-records"), user)).at("/data/items").size());
        assertEquals(
                0, call(200, auth(get("/api/v1/emotion-records"), other)).at("/data/items").size());
        call(404, auth(get("/api/v1/emotion-records/" + id), other));
        call(404, auth(json(patch("/api/v1/emotion-records/" + id), "{\"memo\":null}"), other));
        call(404, auth(delete("/api/v1/emotion-records/" + id), other));
        assertEquals(
                "hello",
                call(200, auth(get("/api/v1/emotion-records/" + id), user))
                        .at("/data/memo")
                        .asText());
        var updated =
                call(
                        200,
                        auth(
                                json(patch("/api/v1/emotion-records/" + id), "{\"memo\":null}"),
                                user));
        assertTrue(updated.at("/data/memo").isNull());
        assertEquals(2, updated.at("/data/emotions").size());
        call(409, auth(json(post("/api/v1/emotion-records"), "{\"emotionIds\":[\"1\"]}"), user));
        call(204, auth(delete("/api/v1/emotion-records/" + id), user));
        assertEquals(
                0,
                db.queryForObject("SELECT count(*) FROM emotion_record_emotions", Integer.class));
    }

    @Test
    void strictValidationAndPatchPreservesHistoricalEmotion() throws Exception {
        call(400, auth(json(post("/api/v1/emotion-records"), "{\"emotionIds\":[\"3\"]}"), user));
        call(
                400,
                auth(
                        json(post("/api/v1/emotion-records"), "{\"emotionIds\":[\"1\",\"1\"]}"),
                        user));
        call(400, auth(json(post("/api/v1/emotion-records"), "{\"emotionIds\":[1]}"), user));
        call(
                400,
                auth(
                        json(
                                post("/api/v1/emotion-records"),
                                "{\"emotionIds\":[\"1\"],\"userId\":\"2\"}"),
                        user));
        String id = createRecord();
        db.update("UPDATE emotions SET active=false WHERE id=1");
        var r =
                call(
                        200,
                        auth(
                                json(
                                        patch("/api/v1/emotion-records/" + id),
                                        "{\"emotionIds\":[\"1\"]}"),
                                user));
        assertEquals("hello", r.at("/data/memo").asText());
        call(400, auth(json(patch("/api/v1/emotion-records/" + id), "{}"), user));
        call(
                400,
                auth(
                        json(
                                patch("/api/v1/emotion-records/" + id),
                                "{\"memo\":\"" + "a".repeat(1001) + "\"}"),
                        user));
        call(400, auth(get("/api/v1/emotion-records").param("from", "2026-11-10"), user));
        call(400, auth(get("/api/v1/emotion-records").param("size", "101"), user));
        call(400, auth(get("/api/v1/emotion-records/9223372036854775808"), user));
    }
}
