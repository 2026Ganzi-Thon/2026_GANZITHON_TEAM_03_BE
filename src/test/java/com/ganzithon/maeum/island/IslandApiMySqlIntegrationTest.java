package com.ganzithon.maeum.island;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class IslandApiMySqlIntegrationTest extends ActivityMySqlTestSupport {
    @AfterEach
    void clean() {
        db.update("UPDATE island_items SET active=true");
        clearFixtures(
                "island_purchases", "wool_transactions", "wool_wallets", "api_idempotency_requests");
    }

    private void wallet(long member, int balance) {
        db.update(
                "INSERT INTO wool_wallets(user_id,balance,updated_at) VALUES(?,?,?)",
                member,
                balance,
                LocalDateTime.ofInstant(clock.now, ZoneOffset.UTC));
    }

    private int balance(long member) {
        return db.queryForObject(
                "SELECT balance FROM wool_wallets WHERE user_id=?", Integer.class, member);
    }

    private int count(String table, long member) {
        return db.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE user_id=?", Integer.class, member);
    }

    private MockHttpServletRequestBuilder buy(String item, String key, long member) {
        return auth(post("/api/v1/island/items/" + item + "/purchases"), member)
                .header("Idempotency-Key", key);
    }

    private JsonNode items(long member) throws Exception {
        return call(200, auth(get("/api/v1/island/items"), member)).get("data");
    }

    private JsonNode item(JsonNode data, String id) {
        for (var i : data.get("items")) if (i.get("itemId").asText().equals(id)) return i;
        return null;
    }

    @Test
    void listReturnsSevenItemsInDisplayOrderWithoutCreatingWallet() throws Exception {
        var data = items(user);
        assertEquals(0, data.get("woolBalance").asInt());
        assertEquals(0, count("wool_wallets", user));
        var ids = new ArrayList<String>();
        for (var i : data.get("items")) {
            ids.add(i.get("itemId").asText());
            assertTrue(i.get("itemId").isTextual());
            assertFalse(i.get("owned").asBoolean());
            assertTrue(i.get("imageUrl").isNull());
            assertTrue(i.get("placedImageUrl").isNull());
            assertEquals(9, i.size());
        }
        assertEquals(List.of("1", "2", "7", "9", "10", "11", "12"), ids);
        var olive = item(data, "1");
        assertEquals("올리브 나무", olive.get("name").asText());
        assertEquals("PLANT", olive.get("category").asText());
        assertEquals(1, olive.get("price").asInt());
        assertEquals(2, olive.get("placedOrder").asInt());
        assertTrue(olive.get("floatAt").isNull());
        var boat = item(data, "11");
        assertEquals(65.5, boat.at("/floatAt/x").asDouble());
        assertEquals(85.0, boat.at("/floatAt/y").asDouble());
        assertEquals("PATH", item(data, "12").get("category").asText());
    }

    @Test
    void listHidesInactiveItems() throws Exception {
        db.update("UPDATE island_items SET active=false WHERE id=2");
        assertNull(item(items(user), "2"));
        assertEquals(6, items(user).get("items").size());
    }

    @Test
    void purchaseSpendsWoolRecordsTransactionAndMarksOwned() throws Exception {
        wallet(user, 30);
        var r = call(201, buy("2", key(), user));
        assertEquals(29, r.at("/data/woolBalance").asInt());
        assertEquals("2", r.at("/data/item/itemId").asText());
        assertTrue(r.at("/data/item/owned").asBoolean());
        assertEquals(1, r.at("/data/item/price").asInt());
        assertEquals(29, balance(user));
        var t =
                db.queryForMap(
                        "SELECT * FROM wool_transactions WHERE user_id=?", user);
        assertEquals("SPEND", t.get("transaction_type"));
        assertEquals(-1, ((Number) t.get("amount")).intValue());
        assertNull(t.get("care_completion_id"));
        var p = db.queryForMap("SELECT * FROM island_purchases WHERE user_id=?", user);
        assertEquals(2L, ((Number) p.get("item_id")).longValue());
        assertEquals(1, ((Number) p.get("price")).intValue());
        assertEquals(t.get("id"), p.get("wool_transaction_id"));
        var data = items(user);
        assertEquals(29, data.get("woolBalance").asInt());
        assertTrue(item(data, "2").get("owned").asBoolean());
        assertFalse(item(data, "1").get("owned").asBoolean());
        var tx = call(200, auth(get("/api/v1/wool-transactions"), user)).at("/data/items/0");
        assertEquals("SPEND", tx.get("transactionType").asText());
        assertEquals(-1, tx.get("amount").asInt());
    }

    @Test
    void purchaseWithExactBalanceLeavesZero() throws Exception {
        wallet(user, 1);
        assertEquals(0, call(201, buy("1", key(), user)).at("/data/woolBalance").asInt());
        assertEquals(0, balance(user));
    }

    @Test
    void insufficientBalanceConflictsWithoutSpending() throws Exception {
        wallet(user, 0);
        assertEquals(
                "WOOL_BALANCE_INSUFFICIENT", call(409, buy("2", key(), user)).get("code").asText());
        assertEquals(0, balance(user));
        assertEquals(0, count("wool_transactions", user));
        assertEquals(0, count("island_purchases", user));
        assertEquals(0, count("api_idempotency_requests", user));
    }

    @Test
    void missingWalletIsInsufficientAndDoesNotCreateWallet() throws Exception {
        assertEquals(
                "WOOL_BALANCE_INSUFFICIENT", call(409, buy("1", key(), user)).get("code").asText());
        assertEquals(0, count("wool_wallets", user));
        assertEquals(0, count("island_purchases", user));
    }

    @Test
    void alreadyOwnedConflictsWithoutSpending() throws Exception {
        wallet(user, 100);
        call(201, buy("1", key(), user));
        assertEquals(
                "ISLAND_ITEM_ALREADY_OWNED", call(409, buy("1", key(), user)).get("code").asText());
        assertEquals(99, balance(user));
        assertEquals(1, count("wool_transactions", user));
        assertEquals(1, count("island_purchases", user));
    }

    @Test
    void unknownOrInactiveItemIsNotFound() throws Exception {
        wallet(user, 100);
        db.update("UPDATE island_items SET active=false WHERE id=2");
        for (String id : new String[] {"3", "999", "2"})
            assertEquals(
                    "ISLAND_ITEM_NOT_FOUND", call(404, buy(id, key(), user)).get("code").asText());
        assertEquals(100, balance(user));
        assertEquals(0, count("island_purchases", user));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        wallet(user, 100);
        call(400, json(buy("1", key(), user), "{}"));
        call(400, json(buy("1", key(), user), "{\"itemId\":\"1\"}"));
        call(400, auth(post("/api/v1/island/items/1/purchases"), user));
        call(400, buy("1", "not-a-uuid", user));
        call(400, buy("abc", key(), user));
        call(400, buy("0", key(), user));
        assertEquals(100, balance(user));
        assertEquals(0, count("island_purchases", user));
    }

    @Test
    void sameKeyReplaysFirstResponseAndSpendsOnce() throws Exception {
        wallet(user, 100);
        String k = key();
        var first = call(201, buy("1", k, user));
        clock.now = clock.now.plusSeconds(60);
        var second = call(201, buy("1", k, user));
        assertEquals(first, second);
        assertEquals(99, balance(user));
        assertEquals(1, count("wool_transactions", user));
    }

    @Test
    void sameKeyDifferentItemConflicts() throws Exception {
        wallet(user, 100);
        String k = key();
        call(201, buy("1", k, user));
        assertEquals(
                "COMMON_IDEMPOTENCY_CONFLICT", call(409, buy("2", k, user)).get("code").asText());
        assertEquals(99, balance(user));
        assertEquals(1, count("island_purchases", user));
    }

    @Test
    void ownershipIsSeparatedPerUser() throws Exception {
        wallet(user, 100);
        wallet(other, 50);
        call(201, buy("1", key(), user));
        var mine = items(user);
        var theirs = items(other);
        assertTrue(item(mine, "1").get("owned").asBoolean());
        assertFalse(item(theirs, "1").get("owned").asBoolean());
        assertEquals(50, theirs.get("woolBalance").asInt());
        call(201, buy("1", key(), other));
        assertEquals(49, balance(other));
        assertEquals(99, balance(user));
    }

    @Test
    void concurrentPurchasesOfSameItemSpendOnce() throws Exception {
        wallet(user, 100);
        var statuses = run(user, List.of("1", "1", "1", "1", "1", "1"), false);
        assertEquals(1, statuses.getOrDefault("201", 0));
        assertEquals(5, statuses.getOrDefault("ISLAND_ITEM_ALREADY_OWNED", 0));
        assertEquals(99, balance(user));
        assertEquals(1, count("wool_transactions", user));
        assertEquals(1, count("island_purchases", user));
    }

    @Test
    void concurrentPurchasesWithSameKeySpendOnce() throws Exception {
        wallet(user, 100);
        var statuses = run(user, List.of("1", "1", "1", "1", "1", "1"), true);
        assertEquals(6, statuses.getOrDefault("201", 0));
        assertEquals(99, balance(user));
        assertEquals(1, count("wool_transactions", user));
    }

    @Test
    void concurrentPurchasesAtBalanceBoundaryNeverGoNegative() throws Exception {
        wallet(user, 2);
        var statuses = run(user, List.of("1", "7", "9", "11"), false);
        assertEquals(2, statuses.getOrDefault("201", 0));
        assertEquals(2, statuses.getOrDefault("WOOL_BALANCE_INSUFFICIENT", 0));
        assertEquals(0, balance(user));
        assertEquals(2, count("wool_transactions", user));
        assertEquals(
                -2,
                db.queryForObject(
                        "SELECT SUM(amount) FROM wool_transactions WHERE user_id=?",
                        Integer.class,
                        user));
    }

    @Test
    void requiresAuthentication() throws Exception {
        call(401, get("/api/v1/island/items"));
        call(
                401,
                post("/api/v1/island/items/1/purchases")
                        .header("Idempotency-Key", key())
                        .header("Authorization", "Bearer forged"));
    }

    private Map<String, Integer> run(long member, List<String> itemIds, boolean sameKey)
            throws Exception {
        String shared = key();
        var jobs = new ArrayList<Callable<String>>();
        for (String id : itemIds) {
            String k = sameKey ? shared : key();
            jobs.add(
                    () -> {
                        var res = mvc.perform(buy(id, k, member)).andReturn().getResponse();
                        if (res.getStatus() == 201) return "201";
                        return mapper.readTree(res.getContentAsString()).get("code").asText();
                    });
        }
        var result = new HashMap<String, Integer>();
        try (var pool = Executors.newFixedThreadPool(jobs.size())) {
            for (var f : pool.invokeAll(jobs))
                result.merge(f.get(20, TimeUnit.SECONDS), 1, Integer::sum);
        }
        return result;
    }
}
