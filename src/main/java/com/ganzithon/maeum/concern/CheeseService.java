package com.ganzithon.maeum.concern;

import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Profile("auth")
@Transactional
class CheeseService {
    private final ActivityStore store;
    private final Clock clock;

    CheeseService(ActivityStore s, Clock c) {
        store = s;
        clock = c;
    }

    @Transactional(readOnly = true)
    Object mine(long user, int page, int size) {
        ActivityApi.page(page, size);
        var now = clock.instant();
        var rows =
                store.list(
                        "SELECT id,content,comfort_type,matures_at,opened_at,saved_at,created_at"
                            + " FROM concerns WHERE user_id=? ORDER BY created_at DESC,id DESC"
                            + " LIMIT ? OFFSET ?",
                        user,
                        size + 1,
                        (long) page * size);
        return ActivityApi.pageResult(
                rows.stream()
                        .map(
                                r ->
                                        ActivityApi.obj(
                                                "concernId",
                                                ActivityApi.sid(r.get("id")),
                                                "preview",
                                                ConcernRules.preview(r),
                                                "comfortType",
                                                r.get("comfort_type"),
                                                "status",
                                                ConcernRules.status(r, now),
                                                "maturesAt",
                                                ActivityApi.instant(r.get("matures_at")),
                                                "openedAt",
                                                ActivityApi.instant(r.get("opened_at")),
                                                "createdAt",
                                                ActivityApi.instant(r.get("created_at"))))
                        .toList(),
                page,
                size);
    }

    @Transactional(readOnly = true)
    Object saved(long user, int page, int size) {
        ActivityApi.page(page, size);
        var rows =
                store.list(
                        "SELECT c.id,c.content,c.comfort_type,c.saved_at,(SELECT COUNT(*) FROM"
                            + " comfort_replies r WHERE r.concern_id=c.id) AS reply_count FROM"
                            + " concerns c WHERE c.user_id=? AND c.saved_at IS NOT NULL ORDER BY"
                            + " c.saved_at DESC,c.id DESC LIMIT ? OFFSET ?",
                        user,
                        size + 1,
                        (long) page * size);
        return ActivityApi.pageResult(
                rows.stream()
                        .map(
                                r ->
                                        ActivityApi.obj(
                                                "concernId",
                                                ActivityApi.sid(r.get("id")),
                                                "preview",
                                                ConcernRules.preview(r),
                                                "comfortType",
                                                r.get("comfort_type"),
                                                "replyCount",
                                                ((Number) r.get("reply_count")).longValue(),
                                                "savedAt",
                                                ActivityApi.instant(r.get("saved_at"))))
                        .toList(),
                page,
                size);
    }

    Object save(long user, long id) {
        // UPDATE를 첫 쿼리로 실행해 동시 요청이 행 잠금에서 직렬화되게 합니다. 이미 보관된 행은 건드리지 않습니다.
        var now = ConcernRules.now(clock);
        store.update(
                "UPDATE concerns SET saved_at=? WHERE id=? AND user_id=? AND opened_at IS NOT NULL"
                    + " AND saved_at IS NULL",
                ActivityApi.ts(now),
                id,
                user);
        var r =
                store.required(
                        "SELECT id,opened_at,saved_at FROM concerns WHERE id=? AND user_id=?",
                        "CONCERN_NOT_FOUND",
                        id,
                        user);
        if (r.get("saved_at") == null) throw ActivityApi.problem(409, "CONCERN_NOT_OPENED");
        return ActivityApi.obj(
                "concernId",
                ActivityApi.sid(r.get("id")),
                "status",
                "SAVED",
                "savedAt",
                ActivityApi.instant(r.get("saved_at")));
    }

    Object unsave(long user, long id) {
        store.update("UPDATE concerns SET saved_at=NULL WHERE id=? AND user_id=?", id, user);
        var r =
                store.required(
                        "SELECT id,matures_at,opened_at,saved_at FROM concerns WHERE id=? AND"
                            + " user_id=?",
                        "CONCERN_NOT_FOUND",
                        id,
                        user);
        return ActivityApi.obj(
                "concernId",
                ActivityApi.sid(r.get("id")),
                "status",
                ConcernRules.status(r, clock.instant()),
                "savedAt",
                null);
    }
}
