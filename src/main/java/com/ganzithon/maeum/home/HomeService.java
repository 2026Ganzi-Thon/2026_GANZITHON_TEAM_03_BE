package com.ganzithon.maeum.home;

import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Profile("auth")
@Transactional(readOnly = true)
class HomeService {
    record Summary(
            String nickname,
            int woolBalance,
            String today,
            TodayEmotion todayEmotion,
            long maturedConcernCount) {}

    record TodayEmotion(boolean recorded, String recordId, List<EmotionItem> emotions) {}

    record EmotionItem(String emotionId, String code, String name) {}

    private final ActivityStore store;
    private final Clock clock;

    HomeService(ActivityStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    Summary home(long user) {
        var instant = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var today = ActivityApi.day(instant);
        var nickname = store.required("SELECT nickname FROM users WHERE id=?", "USER_NOT_FOUND", user);
        var wallet = store.one("SELECT balance FROM wool_wallets WHERE user_id=?", user);
        var matured =
                store.one(
                        "SELECT COUNT(*) AS c FROM concerns WHERE user_id=? AND matures_at<=?"
                                + " AND opened_at IS NULL",
                        user,
                        ActivityApi.ts(instant));
        return new Summary(
                (String) nickname.get("nickname"),
                wallet == null ? 0 : ((Number) wallet.get("balance")).intValue(),
                today.toString(),
                todayEmotion(user, today),
                ((Number) matured.get("c")).longValue());
    }

    private TodayEmotion todayEmotion(long user, LocalDate today) {
        var record =
                store.one(
                        "SELECT id FROM emotion_records WHERE user_id=? AND record_date=?",
                        user,
                        today);
        if (record == null) return new TodayEmotion(false, null, List.of());
        var emotions =
                store.list(
                                "SELECT e.id,e.code,e.name FROM emotions e JOIN"
                                    + " emotion_record_emotions x ON x.emotion_id=e.id WHERE"
                                    + " x.emotion_record_id=? ORDER BY e.display_order,e.id",
                                record.get("id"))
                        .stream()
                        .map(
                                e ->
                                        new EmotionItem(
                                                ActivityApi.sid(e.get("id")),
                                                (String) e.get("code"),
                                                (String) e.get("name")))
                        .toList();
        return new TodayEmotion(true, ActivityApi.sid(record.get("id")), emotions);
    }
}
