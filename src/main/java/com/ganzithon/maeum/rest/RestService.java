package com.ganzithon.maeum.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;
import com.ganzithon.maeum.global.activity.Idempotency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Profile("auth")
@Transactional
class RestService {
    private final ActivityStore store;
    private final Clock clock;
    private final Idempotency idem;
    private final int min, max, memoLimit;

    RestService(
            ActivityStore s,
            Clock c,
            Idempotency i,
            @Value("${app.activity.rest-min-seconds:60}") int min,
            @Value("${app.activity.rest-max-seconds:3600}") int max,
            @Value("${app.activity.after-memo-limit:500}") int limit) {
        store = s;
        clock = c;
        idem = i;
        this.min = min;
        this.max = max;
        memoLimit = limit;
    }

    private Map<String, Object> session(Map<String, Object> r, Instant now) {
        String status = (String) r.get("status");
        Instant end = ActivityApi.instant(r.get("ended_at"));
        if ("RUNNING".equals(status)
                && !ActivityApi.instant(r.get("scheduled_end_at")).isAfter(now)) {
            status = "COMPLETED";
            end = ActivityApi.instant(r.get("scheduled_end_at"));
        }
        return ActivityApi.obj(
                "sessionId",
                ActivityApi.sid(r.get("id")),
                "plannedDurationSeconds",
                r.get("planned_duration_seconds"),
                "startedAt",
                ActivityApi.instant(r.get("started_at")),
                "scheduledEndAt",
                ActivityApi.instant(r.get("scheduled_end_at")),
                "endedAt",
                end,
                "status",
                status,
                "afterMemo",
                r.get("after_memo"));
    }

    private void reconcile(long user, Instant now) {
        store.update(
                "UPDATE rest_sessions SET status='COMPLETED',ended_at=scheduled_end_at WHERE"
                    + " user_id=? AND status='RUNNING' AND scheduled_end_at<=?",
                user,
                ActivityApi.ts(now));
    }

    Idempotency.Reply start(long user, String key, JsonNode body) {
        ActivityApi.fields(body, "plannedDurationSeconds");
        var v = body.get("plannedDurationSeconds");
        if (v == null
                || !v.isIntegralNumber()
                || !v.canConvertToInt()
                || v.intValue() < min
                || v.intValue() > max) throw ActivityApi.problem(400, "REST_INVALID_DURATION");
        store.lockUser(user);
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return idem.execute(
                user,
                key,
                "POST /api/v1/rest-sessions",
                body,
                now,
                201,
                () -> {
                    reconcile(user, now);
                    if (store.one(
                                    "SELECT id FROM rest_sessions WHERE user_id=? AND"
                                        + " status='RUNNING'",
                                    user)
                            != null) throw ActivityApi.problem(409, "REST_ALREADY_RUNNING");
                    long id =
                            store.insert(
                                    "INSERT INTO"
                                        + " rest_sessions(user_id,planned_duration_seconds,started_at,scheduled_end_at,status)"
                                        + " VALUES(?,?,?,?,'RUNNING')",
                                    user,
                                    v.intValue(),
                                    ActivityApi.ts(now),
                                    ActivityApi.ts(now.plusSeconds(v.intValue())));
                    return ActivityApi.obj(
                            "serverTime", now, "session", session(row(user, id), now));
                });
    }

    private Map<String, Object> row(long user, long id) {
        return store.required(
                "SELECT * FROM rest_sessions WHERE id=? AND user_id=?",
                "REST_SESSION_NOT_FOUND",
                id,
                user);
    }

    @Transactional(readOnly = true)
    Object current(long user) {
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var r =
                store.one(
                        "SELECT * FROM rest_sessions WHERE user_id=? ORDER BY started_at DESC,id"
                            + " DESC LIMIT 1",
                        user);
        var s = r == null ? null : session(r, now);
        long remaining = 0;
        if (s != null && "RUNNING".equals(s.get("status"))) {
            var duration = Duration.between(now, (Instant) s.get("scheduledEndAt"));
            remaining = duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
        }
        return ActivityApi.obj("serverTime", now, "remainingSeconds", remaining, "session", s);
    }

    Object cancel(long user, long id) {
        store.lockUser(user);
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        row(user, id);
        reconcile(user, now);
        var r = row(user, id);
        if ("RUNNING".equals(r.get("status")))
            store.update(
                    "UPDATE rest_sessions SET status='CANCELLED',ended_at=? WHERE id=?",
                    ActivityApi.ts(now),
                    id);
        return ActivityApi.obj("session", session(row(user, id), now));
    }

    Object memo(long user, long id, JsonNode body) {
        ActivityApi.fields(body, "afterMemo");
        if (!body.has("afterMemo")) throw ActivityApi.bad();
        String memo = ActivityApi.memo(body.get("afterMemo"), memoLimit);
        store.lockUser(user);
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        row(user, id);
        reconcile(user, now);
        if ("RUNNING".equals(row(user, id).get("status")))
            throw ActivityApi.problem(409, "REST_NOT_ENDED");
        store.update("UPDATE rest_sessions SET after_memo=? WHERE id=?", memo, id);
        return ActivityApi.obj("session", session(row(user, id), now));
    }
}
