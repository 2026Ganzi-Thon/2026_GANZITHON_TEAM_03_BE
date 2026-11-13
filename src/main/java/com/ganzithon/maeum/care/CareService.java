package com.ganzithon.maeum.care;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;
import com.ganzithon.maeum.global.activity.Idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Profile("auth")
@Transactional
class CareService {
    private final ActivityStore store;
    private final Clock clock;
    private final Idempotency idem;

    CareService(ActivityStore s, Clock c, Idempotency i) {
        store = s;
        clock = c;
        idem = i;
    }

    @Transactional(readOnly = true)
    Object actions() {
        return ActivityApi.obj(
                "items",
                store
                        .list(
                                "SELECT * FROM care_actions WHERE active=true ORDER BY"
                                    + " display_order,id")
                        .stream()
                        .map(
                                r ->
                                        ActivityApi.obj(
                                                "careActionId",
                                                ActivityApi.sid(r.get("id")),
                                                "name",
                                                r.get("name"),
                                                "description",
                                                r.get("description"),
                                                "rewardAmount",
                                                r.get("reward_amount"),
                                                "displayOrder",
                                                r.get("display_order")))
                        .toList());
    }

    private Map<String, Object> completion(Map<String, Object> r) {
        var earning =
                store.one(
                        "SELECT t.id FROM wool_transactions t WHERE t.care_completion_id=? AND"
                            + " t.user_id=? AND t.transaction_type='EARN' AND NOT EXISTS (SELECT 1"
                            + " FROM wool_transactions v WHERE v.reverses_transaction_id=t.id)"
                            + " ORDER BY t.id DESC LIMIT 1",
                        r.get("id"),
                        r.get("user_id"));
        return ActivityApi.obj(
                "completionId",
                ActivityApi.sid(r.get("id")),
                "careActionId",
                ActivityApi.sid(r.get("care_action_id")),
                "actionDate",
                r.get("action_date").toString(),
                "rewardAmount",
                r.get("reward_amount"),
                "isCompleted",
                r.get("cancelled_at") == null,
                "completedAt",
                ActivityApi.instant(r.get("completed_at")),
                "cancelledAt",
                ActivityApi.instant(r.get("cancelled_at")),
                "earningTransactionId",
                r.get("cancelled_at") == null && earning != null
                        ? ActivityApi.sid(earning.get("id"))
                        : null);
    }

    @Transactional(readOnly = true)
    Object today(long user) {
        var day = ActivityApi.today(clock);
        return ActivityApi.obj(
                "actionDate",
                day,
                "items",
                store
                        .list(
                                "SELECT * FROM care_completions WHERE user_id=? AND action_date=?"
                                    + " ORDER BY id",
                                user,
                                day)
                        .stream()
                        .map(this::completion)
                        .toList());
    }

    private int balance(long user) {
        var w = store.one("SELECT balance FROM wool_wallets WHERE user_id=?", user);
        return w == null ? 0 : ((Number) w.get("balance")).intValue();
    }

    private void ensureWallet(long user, Instant now) {
        if (store.one("SELECT user_id FROM wool_wallets WHERE user_id=?", user) == null)
            store.update(
                    "INSERT INTO wool_wallets(user_id,balance,updated_at) VALUES(?,0,?)",
                    user,
                    ActivityApi.ts(now));
    }

    Idempotency.Reply complete(long user, long action, String key) {
        store.lockUser(user);
        var now = clock.instant();
        var day = ActivityApi.day(now);
        return idem.execute(
                user,
                key,
                "POST /api/v1/care-actions/" + action + "/completions",
                null,
                now,
                200,
                () -> {
                    var a =
                            store.required(
                                    "SELECT * FROM care_actions WHERE id=?",
                                    "CARE_ACTION_NOT_FOUND",
                                    action);
                    if (!ActivityApi.bool(a.get("active")))
                        throw ActivityApi.problem(409, "CARE_ACTION_INACTIVE");
                    var old =
                            store.one(
                                    "SELECT * FROM care_completions WHERE user_id=? AND"
                                        + " care_action_id=? AND action_date=?",
                                    user,
                                    action,
                                    day);
                    if (old != null && old.get("cancelled_at") == null)
                        throw ActivityApi.problem(409, "CARE_ALREADY_COMPLETED");
                    int reward = ((Number) a.get("reward_amount")).intValue();
                    ensureWallet(user, now);
                    long total = (long) balance(user) + reward;
                    if (total > Integer.MAX_VALUE)
                        throw ActivityApi.problem(409, "WOOL_BALANCE_LIMIT_EXCEEDED");
                    long id;
                    if (old == null)
                        id =
                                store.insert(
                                        "INSERT INTO"
                                            + " care_completions(user_id,care_action_id,action_date,reward_amount,completed_at)"
                                            + " VALUES(?,?,?,?,?)",
                                        user,
                                        action,
                                        day,
                                        reward,
                                        ActivityApi.ts(now));
                    else {
                        id = ((Number) old.get("id")).longValue();
                        store.update(
                                "UPDATE care_completions SET"
                                    + " reward_amount=?,completed_at=?,cancelled_at=NULL WHERE"
                                    + " id=?",
                                reward,
                                ActivityApi.ts(now),
                                id);
                    }
                    store.insert(
                            "INSERT INTO"
                                + " wool_transactions(user_id,amount,transaction_type,care_completion_id,created_at)"
                                + " VALUES(?,?,'EARN',?,?)",
                            user,
                            reward,
                            id,
                            ActivityApi.ts(now));
                    store.update(
                            "UPDATE wool_wallets SET balance=?,updated_at=? WHERE user_id=?",
                            total,
                            ActivityApi.ts(now),
                            user);
                    return ActivityApi.obj(
                            "completion",
                            completion(store.one("SELECT * FROM care_completions WHERE id=?", id)),
                            "balance",
                            (int) total);
                });
    }

    Idempotency.Reply cancel(long user, long id, String key, JsonNode body) {
        ActivityApi.fields(body, "earningTransactionId");
        var e = body.get("earningTransactionId");
        if (e == null || !e.isTextual()) throw ActivityApi.bad();
        long original = ActivityApi.id(e.textValue());
        store.lockUser(user);
        var now = clock.instant();
        var day = ActivityApi.day(now);
        return idem.execute(
                user,
                key,
                "POST /api/v1/care-completions/" + id + "/cancel",
                body,
                now,
                200,
                () -> {
                    var c =
                            store.required(
                                    "SELECT * FROM care_completions WHERE id=? AND user_id=?",
                                    "CARE_COMPLETION_NOT_FOUND",
                                    id,
                                    user);
                    if (!day.toString().equals(c.get("action_date").toString()))
                        throw ActivityApi.problem(409, "CARE_CANCEL_DATE_EXPIRED");
                    if (c.get("cancelled_at") != null)
                        throw ActivityApi.problem(409, "CARE_ALREADY_CANCELLED");
                    var t =
                            store.one(
                                    "SELECT * FROM wool_transactions WHERE id=? AND user_id=? AND"
                                        + " care_completion_id=? AND transaction_type='EARN'",
                                    original,
                                    user,
                                    id);
                    if (t == null
                            || store.one(
                                            "SELECT id FROM wool_transactions WHERE"
                                                + " reverses_transaction_id=?",
                                            original)
                                    != null)
                        throw ActivityApi.problem(409, "CARE_COMPLETION_CYCLE_CHANGED");
                    int amount = ((Number) t.get("amount")).intValue(), current = balance(user);
                    if (current < amount)
                        throw ActivityApi.problem(409, "WOOL_BALANCE_INSUFFICIENT");
                    store.update(
                            "UPDATE care_completions SET cancelled_at=? WHERE id=?",
                            ActivityApi.ts(now),
                            id);
                    long reversal =
                            store.insert(
                                    "INSERT INTO"
                                        + " wool_transactions(user_id,amount,transaction_type,care_completion_id,reverses_transaction_id,created_at)"
                                        + " VALUES(?,?,'REVERSE',?,?,?)",
                                    user,
                                    -amount,
                                    id,
                                    original,
                                    ActivityApi.ts(now));
                    store.update(
                            "UPDATE wool_wallets SET balance=?,updated_at=? WHERE user_id=?",
                            current - amount,
                            ActivityApi.ts(now),
                            user);
                    return ActivityApi.obj(
                            "completion",
                            completion(store.one("SELECT * FROM care_completions WHERE id=?", id)),
                            "reversalTransactionId",
                            Long.toString(reversal),
                            "balance",
                            current - amount);
                });
    }

    @Transactional(readOnly = true)
    Object wallet(long user) {
        var r = store.one("SELECT * FROM wool_wallets WHERE user_id=?", user);
        return ActivityApi.obj(
                "balance",
                r == null ? 0 : r.get("balance"),
                "updatedAt",
                r == null ? null : ActivityApi.instant(r.get("updated_at")));
    }

    @Transactional(readOnly = true)
    Object transactions(long user, int page, int size) {
        ActivityApi.page(page, size);
        var rows =
                store.list(
                        "SELECT * FROM wool_transactions WHERE user_id=? ORDER BY created_at"
                            + " DESC,id DESC LIMIT ? OFFSET ?",
                        user,
                        size + 1,
                        (long) page * size);
        return ActivityApi.pageResult(
                rows.stream()
                        .map(
                                r ->
                                        ActivityApi.obj(
                                                "transactionId",
                                                ActivityApi.sid(r.get("id")),
                                                "amount",
                                                r.get("amount"),
                                                "transactionType",
                                                r.get("transaction_type"),
                                                "careCompletionId",
                                                ActivityApi.sid(r.get("care_completion_id")),
                                                "reversesTransactionId",
                                                ActivityApi.sid(r.get("reverses_transaction_id")),
                                                "createdAt",
                                                ActivityApi.instant(r.get("created_at"))))
                        .toList(),
                page,
                size);
    }
}
