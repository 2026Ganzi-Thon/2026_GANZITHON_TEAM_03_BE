package com.ganzithon.maeum.home;

import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@Service
@Profile("auth")
@Transactional(readOnly = true)
class HomeService {
    private final ActivityStore store;
    private final Clock clock;

    HomeService(ActivityStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    Object home(long user) {
        var instant = clock.instant();
        var today = ActivityApi.today(clock);
        var nickname = store.required("SELECT nickname FROM users WHERE id=?", "USER_NOT_FOUND", user);
        var wallet = store.one("SELECT balance FROM wool_wallets WHERE user_id=?", user);
        var matured =
                store.one(
                        "SELECT COUNT(*) AS c FROM concerns WHERE user_id=? AND matures_at<=?"
                                + " AND opened_at IS NULL",
                        user,
                        ActivityApi.ts(instant));
        return ActivityApi.obj(
                "nickname",
                nickname.get("nickname"),
                "woolBalance",
                wallet == null ? 0 : wallet.get("balance"),
                "today",
                today.toString(),
                "todayEmotion",
                todayEmotion(user, today),
                "maturedConcernCount",
                ((Number) matured.get("c")).longValue());
    }

    private Map<String, Object> todayEmotion(long user, java.time.LocalDate today) {
        var record =
                store.one(
                        "SELECT id FROM emotion_records WHERE user_id=? AND record_date=?",
                        user,
                        today);
        if (record == null) return ActivityApi.obj("recorded", false, "recordId", null, "emotions", List.of());
        var emotions =
                store.list(
                                "SELECT e.id,e.code,e.name FROM emotions e JOIN"
                                    + " emotion_record_emotions x ON x.emotion_id=e.id WHERE"
                                    + " x.emotion_record_id=? ORDER BY e.display_order,e.id",
                                record.get("id"))
                        .stream()
                        .map(
                                e ->
                                        ActivityApi.obj(
                                                "emotionId",
                                                ActivityApi.sid(e.get("id")),
                                                "code",
                                                e.get("code"),
                                                "name",
                                                e.get("name")))
                        .toList();
        return ActivityApi.obj(
                "recorded", true, "recordId", ActivityApi.sid(record.get("id")), "emotions", emotions);
    }
}
