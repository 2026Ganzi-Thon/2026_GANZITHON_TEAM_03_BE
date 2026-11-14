package com.ganzithon.maeum.concern;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Profile("auth")
@Transactional
class ComfortReplyService {
    private static final int REPLY_LIMIT = 500;
    private static final List<String> DEFAULT_COMFORTS =
            List.of(
                    "여기까지 온 것만으로도 충분히 잘하고 있어요.",
                    "혼자 끌어안고 있던 마음을 꺼내 놓은 것만으로도 큰 용기였어요.",
                    "지금 느끼는 감정은 자연스러운 것이니 스스로를 너무 다그치지 않아도 괜찮아요.",
                    "힘든 시간도 천천히 지나갈 거예요. 오늘은 조금 쉬어가도 좋아요.");

    private final ActivityStore store;
    private final Clock clock;

    ComfortReplyService(ActivityStore s, Clock c) {
        store = s;
        clock = c;
    }

    Object reply(long user, long id, JsonNode body) {
        ActivityApi.fields(body, "content");
        var c = body.get("content");
        if (c == null || !c.isTextual()) throw ActivityApi.bad();
        String content = c.textValue().strip();
        if (content.isEmpty() || content.codePointCount(0, content.length()) > REPLY_LIMIT)
            throw ActivityApi.bad();
        // 공유 잠금으로 위로 저장이 끝날 때까지 개봉·삭제가 기다리게 해, 개봉 뒤에 위로가 들어오거나
        // 기본 위로와 사용자 위로가 함께 저장되지 않게 합니다.
        var concern =
                store.required(
                        "SELECT user_id,matures_at,opened_at FROM concerns WHERE id=? FOR SHARE",
                        "CONCERN_NOT_FOUND",
                        id);
        if (((Number) concern.get("user_id")).longValue() == user)
            throw ActivityApi.problem(403, "REPLY_OWN_CONCERN_FORBIDDEN");
        var now = ConcernRules.now(clock);
        if (concern.get("opened_at") != null
                || !now.isBefore(ActivityApi.instant(concern.get("matures_at"))))
            throw ActivityApi.problem(409, "CONCERN_REPLY_CLOSED");
        if (store.one(
                        "SELECT id FROM comfort_replies WHERE concern_id=? AND author_id=?",
                        id,
                        user)
                != null) throw ActivityApi.problem(409, "REPLY_ALREADY_EXISTS");
        long replyId;
        try {
            replyId =
                    store.insert(
                            "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,"
                                + "created_at) VALUES(?,?,'USER',?,?)",
                            id,
                            user,
                            content,
                            ActivityApi.ts(now));
        } catch (DuplicateKeyException e) {
            throw ActivityApi.problem(409, "REPLY_ALREADY_EXISTS");
        }
        return ActivityApi.obj(
                "replyId", Long.toString(replyId), "concernId", Long.toString(id), "createdAt", now);
    }

    Object open(long user, long id) {
        // 개봉 UPDATE를 첫 쿼리로 실행해 동시 요청이 행 잠금에서 직렬화되고, 이후 조회는 커밋된 결과를 보게 합니다.
        var now = ConcernRules.now(clock);
        int opened =
                store.update(
                        "UPDATE concerns SET opened_at=? WHERE id=? AND user_id=? AND opened_at IS"
                            + " NULL AND matures_at<=?",
                        ActivityApi.ts(now),
                        id,
                        user,
                        ActivityApi.ts(now));
        if (opened == 1
                && store.one("SELECT id FROM comfort_replies WHERE concern_id=? LIMIT 1", id)
                        == null) {
            store.insert(
                    "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,"
                        + "created_at) VALUES(?,NULL,'SYSTEM',?,?)",
                    id,
                    DEFAULT_COMFORTS.get(
                            ThreadLocalRandom.current()
                                    .nextInt(DEFAULT_COMFORTS.size())),
                    ActivityApi.ts(now));
        }
        return received(user, id, "CONCERN_NOT_MATURED");
    }

    @Transactional(readOnly = true)
    Object replies(long user, long id) {
        return received(user, id, "CONCERN_NOT_OPENED");
    }

    private Object received(long user, long id, String notOpenedCode) {
        var r =
                store.required(
                        "SELECT id,opened_at,saved_at FROM concerns WHERE id=? AND user_id=?",
                        "CONCERN_NOT_FOUND",
                        id,
                        user);
        if (r.get("opened_at") == null) throw ActivityApi.problem(409, notOpenedCode);
        var replies =
                store
                        .list(
                                "SELECT id,content,source_type,created_at FROM comfort_replies"
                                    + " WHERE concern_id=? ORDER BY created_at,id",
                                id)
                        .stream()
                        .map(
                                x ->
                                        ActivityApi.obj(
                                                "replyId",
                                                ActivityApi.sid(x.get("id")),
                                                "content",
                                                x.get("content"),
                                                "fromSystem",
                                                "SYSTEM".equals(x.get("source_type")),
                                                "createdAt",
                                                ActivityApi.instant(x.get("created_at"))))
                        .toList();
        return ActivityApi.obj(
                "concernId",
                ActivityApi.sid(r.get("id")),
                "status",
                r.get("saved_at") != null ? "SAVED" : "OPENED",
                "openedAt",
                ActivityApi.instant(r.get("opened_at")),
                "replies",
                replies);
    }
}
