package com.ganzithon.maeum.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;
import com.ganzithon.maeum.auth.session.AuthSessionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class CareMySqlIntegrationTest extends ActivityMySqlTestSupport {
    @Autowired AuthSessionService sessions;
    @Autowired TransactionTemplate transactions;

    @BeforeEach
    void masterData() {
        db.update(
                "INSERT INTO care_actions(id,name,description,reward_amount,display_order,active)"
                        + " VALUES(92001,'물 마시기','물 한 잔',10,1,true),(92002,'쉬기',NULL,5,2,true)");
        db.update("UPDATE care_actions SET category='움직이기' WHERE id=92001");
    }

    @AfterEach
    void clean() {
        db.update(
                "DELETE FROM wool_transactions WHERE user_id IN (?,?) AND reverses_transaction_id"
                        + " IS NOT NULL",
                user,
                other);
        clearFixtures(
                "wool_transactions",
                "care_completions",
                "wool_wallets",
                "api_idempotency_requests");
        db.update("DELETE FROM care_actions WHERE id IN (92001,92002)");
    }

    @Test
    void returnsCategoriesAndFallbackWithoutChangingRewardFields() throws Exception {
        var items = call(200, auth(get("/api/v1/care-actions"), user)).at("/data/items");
        for (var item : items) {
            switch (item.get("careActionId").asText()) {
                case "92001" -> {
                    assertEquals("움직이기", item.get("category").asText());
                    assertEquals(10, item.get("rewardAmount").asInt());
                    assertEquals("물 한 잔", item.get("description").asText());
                }
                case "92002" -> assertEquals("기타", item.get("category").asText());
                default -> assertFalse(item.get("category").asText().isBlank());
            }
        }
        assertThat(items.findValuesAsText("careActionId")).contains("92001", "92002");
        assertThat(items.findValuesAsText("category"))
                .contains("움직이기", "마음돌봄", "쉬어가기", "일상가꾸기", "기타");
    }

    @Test
    void rejectsReuseForAnotherAction() throws Exception {
        String sameKey = key();
        call(
                200,
                auth(
                        post("/api/v1/care-actions/92001/completions")
                                .header("Idempotency-Key", sameKey),
                        user));
        assertEquals(
                "COMMON_IDEMPOTENCY_CONFLICT",
                call(
                                409,
                                auth(
                                        post("/api/v1/care-actions/92002/completions")
                                                .header("Idempotency-Key", sameKey),
                                        user))
                        .get("code")
                        .asText());
        call(400, auth(post("/api/v1/care-actions/92002/completions"), user));
        call(
                400,
                auth(
                        json(
                                        post("/api/v1/care-actions/92002/completions"),
                                        "{\"rewardAmount\":10000}")
                                .header("Idempotency-Key", key()),
                        user));
    }

    @Test
    void woolCycleIdempotencyAndOriginalReward() throws Exception {
        assertThat(
                        call(200, auth(get("/api/v1/care-actions"), user))
                                .at("/data/items")
                                .findValuesAsText("careActionId"))
                .contains("92001", "92002");
        assertTrue(
                call(200, auth(get("/api/v1/wool-wallet"), user)).at("/data/updatedAt").isNull());
        String k = key();
        var r =
                call(
                        200,
                        auth(
                                post("/api/v1/care-actions/92001/completions")
                                        .header("Idempotency-Key", k),
                                user));
        assertEquals(
                r,
                call(
                        200,
                        auth(
                                post("/api/v1/care-actions/92001/completions")
                                        .header("Idempotency-Key", k),
                                user)));
        call(
                409,
                auth(
                        post("/api/v1/care-actions/92001/completions")
                                .header("Idempotency-Key", key()),
                        user));
        String id = r.at("/data/completion/completionId").asText(),
                tx = r.at("/data/completion/earningTransactionId").asText();
        assertEquals(
                1,
                call(200, auth(get("/api/v1/care-completions/today"), user))
                        .at("/data/items")
                        .size());
        db.update("UPDATE care_actions SET reward_amount=20 WHERE id=92001");
        String cancelKey = key(), body = "{\"earningTransactionId\":\"" + tx + "\"}";
        var cancelled =
                call(
                        200,
                        auth(
                                json(post("/api/v1/care-completions/" + id + "/cancel"), body)
                                        .header("Idempotency-Key", cancelKey),
                                user));
        assertEquals(0, cancelled.at("/data/balance").asInt());
        call(
                200,
                auth(
                        post("/api/v1/care-actions/92001/completions")
                                .header("Idempotency-Key", key()),
                        user));
        assertEquals(
                cancelled,
                call(
                        200,
                        auth(
                                json(post("/api/v1/care-completions/" + id + "/cancel"), body)
                                        .header("Idempotency-Key", cancelKey),
                                user)));
        assertEquals(
                "CARE_COMPLETION_CYCLE_CHANGED",
                call(
                                409,
                                auth(
                                        json(
                                                        post(
                                                                "/api/v1/care-completions/"
                                                                        + id
                                                                        + "/cancel"),
                                                        body)
                                                .header("Idempotency-Key", key()),
                                        user))
                        .get("code")
                        .asText());
        assertEquals(
                20, call(200, auth(get("/api/v1/wool-wallet"), user)).at("/data/balance").asInt());
        assertEquals(
                3,
                call(200, auth(get("/api/v1/wool-transactions"), user)).at("/data/items").size());
        assertEquals(
                0, call(200, auth(get("/api/v1/wool-wallet"), other)).at("/data/balance").asInt());
        assertEquals(
                20,
                db.queryForObject(
                        "SELECT SUM(amount) FROM wool_transactions WHERE user_id=?",
                        Integer.class,
                        user));
    }

    @Test
    void insufficientBalanceRollbackAndSeoulDay() throws Exception {
        clock.now = Instant.parse("2026-11-10T14:59:59Z");
        String authorization =
                "Bearer " + transactions.execute(status -> sessions.create(user).accessToken());
        var r =
                call(
                        200,
                        post("/api/v1/care-actions/92001/completions")
                                .header("Idempotency-Key", key())
                                .header("Authorization", authorization));
        String id = r.at("/data/completion/completionId").asText(),
                tx = r.at("/data/completion/earningTransactionId").asText();
        db.update("UPDATE wool_wallets SET balance=0 WHERE user_id=?", user);
        String body = "{\"earningTransactionId\":\"" + tx + "\"}";
        call(
                409,
                json(post("/api/v1/care-completions/" + id + "/cancel"), body)
                        .header("Idempotency-Key", key())
                        .header("Authorization", authorization));
        assertNull(
                db.queryForObject(
                        "SELECT cancelled_at FROM care_completions WHERE id=?", Object.class, id));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM wool_transactions", Integer.class));
        clock.now = Instant.parse("2026-11-10T15:00:00Z");
        assertEquals(
                "2026-11-11",
                call(
                                200,
                                get("/api/v1/care-completions/today")
                                        .header("Authorization", authorization))
                        .at("/data/actionDate")
                        .asText());
        assertEquals(
                "CARE_CANCEL_DATE_EXPIRED",
                call(
                                409,
                                json(post("/api/v1/care-completions/" + id + "/cancel"), body)
                                        .header("Idempotency-Key", key())
                                        .header("Authorization", authorization))
                        .get("code")
                        .asText());
    }

    @Test
    void concurrentRetriesPayOnlyOnce() throws Exception {
        String sameKey = key();
        try (var pool = Executors.newFixedThreadPool(6)) {
            var jobs = new ArrayList<Callable<JsonNode>>();
            for (int i = 0; i < 6; i++)
                jobs.add(
                        () ->
                                call(
                                        200,
                                        auth(
                                                post("/api/v1/care-actions/92001/completions")
                                                        .header("Idempotency-Key", sameKey),
                                                user)));
            var results = pool.invokeAll(jobs);
            for (var result : results)
                assertEquals(10, result.get(20, TimeUnit.SECONDS).at("/data/balance").asInt());
        }
        assertEquals(
                10,
                db.queryForObject(
                        "SELECT balance FROM wool_wallets WHERE user_id=?", Integer.class, user));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM wool_transactions", Integer.class));
    }
}
