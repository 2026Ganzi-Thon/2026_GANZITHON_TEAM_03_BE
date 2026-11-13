package com.ganzithon.maeum.emotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Profile("auth")
@Transactional
class EmotionService {
    private final ActivityStore store;
    private final Clock clock;
    private final int memoLimit;

    EmotionService(
            ActivityStore s, Clock c, @Value("${app.activity.emotion-memo-limit:1000}") int limit) {
        store = s;
        clock = c;
        memoLimit = limit;
    }

    @Transactional(readOnly = true)
    Object emotions() {
        return ActivityApi.obj(
                "items",
                store
                        .list("SELECT * FROM emotions WHERE active=true ORDER BY display_order,id")
                        .stream()
                        .map(this::emotion)
                        .toList());
    }

    private Map<String, Object> emotion(Map<String, Object> r) {
        return ActivityApi.obj(
                "emotionId",
                ActivityApi.sid(r.get("id")),
                "code",
                r.get("code"),
                "name",
                r.get("name"),
                "displayOrder",
                r.get("display_order"),
                "active",
                ActivityApi.bool(r.get("active")));
    }

    private Map<String, Object> row(long user, long id) {
        return store.required(
                "SELECT * FROM emotion_records WHERE id=? AND user_id=?",
                "EMOTION_RECORD_NOT_FOUND",
                id,
                user);
    }

    private Map<String, Object> record(Map<String, Object> r) {
        var choices =
                store.list(
                        "SELECT e.* FROM emotions e JOIN emotion_record_emotions x ON"
                            + " x.emotion_id=e.id WHERE x.emotion_record_id=? ORDER BY"
                            + " e.display_order,e.id",
                        r.get("id"));
        return ActivityApi.obj(
                "recordId",
                ActivityApi.sid(r.get("id")),
                "recordDate",
                r.get("record_date").toString(),
                "memo",
                r.get("memo"),
                "emotions",
                choices.stream().map(this::emotion).toList(),
                "createdAt",
                ActivityApi.instant(r.get("created_at")),
                "updatedAt",
                ActivityApi.instant(r.get("updated_at")));
    }

    private Set<Long> selection(JsonNode values, Set<Long> existing) {
        if (values == null || !values.isArray() || values.isEmpty())
            throw ActivityApi.problem(400, "EMOTION_INVALID_SELECTION");
        Set<Long> ids = new LinkedHashSet<>();
        for (var v : values) {
            if (!v.isTextual()) throw ActivityApi.problem(400, "EMOTION_INVALID_SELECTION");
            long id;
            try {
                id = ActivityApi.id(v.textValue());
            } catch (ActivityApi.Problem e) {
                throw ActivityApi.problem(400, "EMOTION_INVALID_SELECTION");
            }
            var e = store.one("SELECT active FROM emotions WHERE id=?", id);
            if (!ids.add(id)
                    || e == null
                    || (!ActivityApi.bool(e.get("active")) && !existing.contains(id)))
                throw ActivityApi.problem(400, "EMOTION_INVALID_SELECTION");
        }
        return ids;
    }

    Object create(long user, JsonNode body) {
        ActivityApi.fields(body, "emotionIds", "memo");
        store.lockUser(user);
        var instant = clock.instant();
        var day = ActivityApi.day(instant);
        var now = ActivityApi.ts(instant);
        var selected = selection(body.get("emotionIds"), Set.of());
        String memo = ActivityApi.memo(body.get("memo"), memoLimit);
        if (store.one("SELECT id FROM emotion_records WHERE user_id=? AND record_date=?", user, day)
                != null) throw ActivityApi.problem(409, "EMOTION_RECORD_ALREADY_EXISTS");
        long id =
                store.insert(
                        "INSERT INTO"
                            + " emotion_records(user_id,record_date,memo,created_at,updated_at)"
                            + " VALUES(?,?,?,?,?)",
                        user,
                        day,
                        memo,
                        now,
                        now);
        for (long e : selected)
            store.update(
                    "INSERT INTO emotion_record_emotions(emotion_record_id,emotion_id) VALUES(?,?)",
                    id,
                    e);
        return record(row(user, id));
    }

    @Transactional(readOnly = true)
    Object get(long user, long id) {
        return record(row(user, id));
    }

    @Transactional(readOnly = true)
    Object list(long user, String from, String to, int page, int size) {
        ActivityApi.page(page, size);
        LocalDate today = ActivityApi.today(clock), start, end;
        try {
            if (from == null && to == null) {
                start = today.withDayOfMonth(1);
                end = today;
            } else {
                if (from == null
                        || to == null
                        || !from.matches("\\d{4}-\\d{2}-\\d{2}")
                        || !to.matches("\\d{4}-\\d{2}-\\d{2}"))
                    throw new IllegalArgumentException();
                start = LocalDate.parse(from);
                end = LocalDate.parse(to);
            }
            if (start.isAfter(end)
                    || end.isAfter(today)
                    || ChronoUnit.DAYS.between(start, end) >= 366)
                throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw ActivityApi.problem(400, "EMOTION_INVALID_DATE_RANGE");
        }
        var rows =
                store.list(
                        "SELECT * FROM emotion_records WHERE user_id=? AND record_date BETWEEN ?"
                            + " AND ? ORDER BY record_date DESC,id DESC LIMIT ? OFFSET ?",
                        user,
                        start,
                        end,
                        size + 1,
                        (long) page * size);
        return ActivityApi.pageResult(rows.stream().map(this::record).toList(), page, size);
    }

    Object patch(long user, long id, JsonNode body) {
        ActivityApi.fields(body, "emotionIds", "memo");
        if (body.isEmpty()) throw ActivityApi.problem(400, "COMMON_EMPTY_UPDATE");
        store.lockUser(user);
        var r = row(user, id);
        if (body.has("emotionIds")) {
            Set<Long> existing = new HashSet<>();
            store.list(
                            "SELECT emotion_id FROM emotion_record_emotions WHERE"
                                + " emotion_record_id=?",
                            id)
                    .forEach(x -> existing.add(((Number) x.get("emotion_id")).longValue()));
            var choices = selection(body.get("emotionIds"), existing);
            store.update("DELETE FROM emotion_record_emotions WHERE emotion_record_id=?", id);
            for (long e : choices)
                store.update(
                        "INSERT INTO emotion_record_emotions(emotion_record_id,emotion_id)"
                            + " VALUES(?,?)",
                        id,
                        e);
        }
        String memo =
                body.has("memo")
                        ? ActivityApi.memo(body.get("memo"), memoLimit)
                        : (String) r.get("memo");
        store.update(
                "UPDATE emotion_records SET memo=?,updated_at=? WHERE id=?",
                memo,
                ActivityApi.ts(clock.instant()),
                id);
        return record(row(user, id));
    }

    void delete(long user, long id) {
        store.lockUser(user);
        row(user, id);
        store.update("DELETE FROM emotion_records WHERE id=? AND user_id=?", id, user);
    }
}
