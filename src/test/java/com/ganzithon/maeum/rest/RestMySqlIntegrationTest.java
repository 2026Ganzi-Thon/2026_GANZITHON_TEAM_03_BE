package com.ganzithon.maeum.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RestMySqlIntegrationTest extends ActivityMySqlTestSupport {

    @AfterEach
    void clean() {
        clearFixtures("rest_sessions", "api_idempotency_requests");
    }

    @Test
    void restLifecycleAndReadDoesNotWrite() throws Exception {
        assertTrue(
                call(200, auth(get("/api/v1/rest-sessions/current"), user))
                        .at("/data/session")
                        .isNull());
        String k = key();
        var request =
                json(post("/api/v1/rest-sessions"), "{\"plannedDurationSeconds\":60}")
                        .header("Idempotency-Key", k);
        var r = call(201, auth(request, user));
        String id = r.at("/data/session/sessionId").asText();
        assertEquals(
                r,
                call(
                        201,
                        auth(
                                json(
                                                post("/api/v1/rest-sessions"),
                                                "{\"plannedDurationSeconds\":60}")
                                        .header("Idempotency-Key", k),
                                user)));
        call(
                409,
                auth(
                        json(post("/api/v1/rest-sessions"), "{\"plannedDurationSeconds\":300}")
                                .header("Idempotency-Key", key()),
                        user));
        call(
                409,
                auth(
                        json(
                                patch("/api/v1/rest-sessions/" + id + "/after-memo"),
                                "{\"afterMemo\":\"hi\"}"),
                        user));
        call(404, auth(post("/api/v1/rest-sessions/" + id + "/cancel"), other));
        clock.now = clock.now.plusSeconds(60);
        var completed = call(200, auth(get("/api/v1/rest-sessions/current"), user));
        assertEquals("COMPLETED", completed.at("/data/session/status").asText());
        assertEquals(
                "RUNNING",
                db.queryForObject("SELECT status FROM rest_sessions WHERE id=?", String.class, id));
        assertEquals(
                "COMPLETED",
                call(200, auth(post("/api/v1/rest-sessions/" + id + "/cancel"), user))
                        .at("/data/session/status")
                        .asText());
        assertEquals(
                "hi",
                call(
                                200,
                                auth(
                                        json(
                                                patch(
                                                        "/api/v1/rest-sessions/"
                                                                + id
                                                                + "/after-memo"),
                                                "{\"afterMemo\":\"hi\"}"),
                                        user))
                        .at("/data/session/afterMemo")
                        .asText());
        assertTrue(
                call(
                                200,
                                auth(
                                        json(
                                                patch(
                                                        "/api/v1/rest-sessions/"
                                                                + id
                                                                + "/after-memo"),
                                                "{\"afterMemo\":null}"),
                                        user))
                        .at("/data/session/afterMemo")
                        .isNull());
    }

    @Test
    void restCancelIsStableAndValidation() throws Exception {
        call(
                400,
                auth(
                        json(post("/api/v1/rest-sessions"), "{\"plannedDurationSeconds\":59}")
                                .header("Idempotency-Key", key()),
                        user));
        call(
                400,
                auth(
                        json(post("/api/v1/rest-sessions"), "{\"plannedDurationSeconds\":60.0}")
                                .header("Idempotency-Key", key()),
                        user));
        var r =
                call(
                        201,
                        auth(
                                json(
                                                post("/api/v1/rest-sessions"),
                                                "{\"plannedDurationSeconds\":60}")
                                        .header("Idempotency-Key", key()),
                                user));
        String id = r.at("/data/session/sessionId").asText();
        clock.now = clock.now.plusSeconds(10);
        var end = call(200, auth(post("/api/v1/rest-sessions/" + id + "/cancel"), user));
        clock.now = clock.now.plusSeconds(10);
        assertEquals(end, call(200, auth(post("/api/v1/rest-sessions/" + id + "/cancel"), user)));
        assertEquals("CANCELLED", end.at("/data/session/status").asText());
        call(400, auth(json(patch("/api/v1/rest-sessions/" + id + "/after-memo"), "{}"), user));
    }
}
