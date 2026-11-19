package com.ganzithon.maeum.concern;

import com.ganzithon.maeum.global.activity.ActivityApi;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Profile("auth")
@Transactional
class ComfortReplyService {
    private static final List<String> DEFAULT_COMFORTS =
            List.of(
                    "여기까지 온 것만으로도 충분히 잘하고 있어요.",
                    "혼자 끌어안고 있던 마음을 꺼내 놓은 것만으로도 큰 용기였어요.",
                    "지금 느끼는 감정은 자연스러운 것이니 스스로를 너무 다그치지 않아도 괜찮아요.",
                    "힘든 시간도 천천히 지나갈 거예요. 오늘은 조금 쉬어가도 좋아요.");
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    private final ConcernRepository concerns;
    private final ComfortReplyRepository replies;
    private final Clock clock;

    ComfortReplyService(ConcernRepository concerns, ComfortReplyRepository replies, Clock c) {
        this.concerns = concerns;
        this.replies = replies;
        clock = c;
    }

    record ReplyResult(String replyId, String concernId, Instant createdAt) {}

    record ReceivedReply(String replyId, String content, boolean fromSystem, Instant createdAt) {}

    record ReceivedReplies(
            String concernId,
            ConcernStatus status,
            Instant openedAt,
            List<ReceivedReply> replies) {}

    ReplyResult reply(long user, long id, ComfortReplyRequest request) {
        // 공유 잠금으로 위로 저장이 끝날 때까지 개봉·삭제가 기다리게 해, 개봉 뒤에 위로가 들어오거나
        // 기본 위로와 사용자 위로가 함께 저장되지 않게 합니다.
        var concern =
                concerns.findForShare(id)
                        .orElseThrow(() -> ActivityApi.problem(404, "CONCERN_NOT_FOUND"));
        var now = ConcernRules.now(clock);
        concern.checkReplyable(user, now);
        if (replies.existsByConcernIdAndAuthorId(id, user))
            throw ActivityApi.problem(409, "REPLY_ALREADY_EXISTS");
        ComfortReply reply;
        try {
            reply = replies.saveAndFlush(ComfortReply.byUser(id, user, request.content(), now));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 사용자가 위로를 보내면 UNIQUE(concern_id, author_id) 위반으로 하나만 남습니다.
            if (isDuplicateEntry(e)) throw ActivityApi.problem(409, "REPLY_ALREADY_EXISTS");
            throw e;
        }
        return new ReplyResult(reply.getId().toString(), Long.toString(id), now);
    }

    ReceivedReplies open(long user, long id) {
        // 내 고민 행을 FOR UPDATE로 먼저 잠가 동시 개봉을 직렬화합니다. 뒤 요청은 이미 열린 상태를 봅니다.
        var concern =
                concerns.findMineForUpdate(id, user)
                        .orElseThrow(() -> ActivityApi.problem(404, "CONCERN_NOT_FOUND"));
        var now = ConcernRules.now(clock);
        if (concern.open(now) && !replies.existsByConcernId(id)) {
            replies.save(
                    ComfortReply.bySystem(
                            id,
                            DEFAULT_COMFORTS.get(
                                    ThreadLocalRandom.current().nextInt(DEFAULT_COMFORTS.size())),
                            now));
        }
        return received(concern, now);
    }

    @Transactional(readOnly = true)
    ReceivedReplies replies(long user, long id) {
        var concern =
                concerns.findByIdAndUserId(id, user)
                        .orElseThrow(() -> ActivityApi.problem(404, "CONCERN_NOT_FOUND"));
        if (concern.getOpenedAt() == null) throw ActivityApi.problem(409, "CONCERN_NOT_OPENED");
        return received(concern, ConcernRules.now(clock));
    }

    private ReceivedReplies received(Concern concern, Instant now) {
        var list =
                replies.findByConcernIdOrderByCreatedAtAscIdAsc(concern.getId()).stream()
                        .map(
                                r ->
                                        new ReceivedReply(
                                                r.getId().toString(),
                                                r.getContent(),
                                                r.isFromSystem(),
                                                r.getCreatedAt()))
                        .toList();
        return new ReceivedReplies(
                concern.getId().toString(), concern.statusAt(now), concern.getOpenedAt(), list);
    }

    private static boolean isDuplicateEntry(Throwable e) {
        for (var t = e; t != null; t = t.getCause())
            if (t instanceof SQLException s && s.getErrorCode() == MYSQL_DUPLICATE_ENTRY)
                return true;
        return false;
    }
}
