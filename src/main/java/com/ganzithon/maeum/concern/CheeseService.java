package com.ganzithon.maeum.concern;

import com.ganzithon.maeum.global.activity.ActivityApi;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@Profile("auth")
@Transactional
class CheeseService {
    record MineItem(
            String concernId,
            String preview,
            ComfortType comfortType,
            ConcernStatus status,
            Instant maturesAt,
            Instant openedAt,
            Instant createdAt) {}

    record SavedItem(
            String concernId,
            String preview,
            ComfortType comfortType,
            long replyCount,
            Instant savedAt) {}

    record SaveResult(String concernId, ConcernStatus status, Instant savedAt) {}

    /** 기존 페이지 응답(items, page, size, hasNext)과 같은 형식입니다. */
    record PageResult<T>(List<T> items, int page, int size, boolean hasNext) {
        static <T> PageResult<T> of(Slice<T> slice) {
            return new PageResult<>(
                    slice.getContent(), slice.getNumber(), slice.getSize(), slice.hasNext());
        }
    }

    private final ConcernRepository concerns;
    private final Clock clock;

    CheeseService(ConcernRepository concerns, Clock c) {
        this.concerns = concerns;
        clock = c;
    }

    @Transactional(readOnly = true)
    PageResult<MineItem> mine(long user, int page, int size) {
        ActivityApi.page(page, size);
        var now = ConcernRules.now(clock);
        return PageResult.of(
                concerns.findByUserIdOrderByCreatedAtDescIdDesc(user, PageRequest.of(page, size))
                        .map(
                                c ->
                                        new MineItem(
                                                c.getId().toString(),
                                                c.preview(),
                                                c.getComfortType(),
                                                c.statusAt(now),
                                                c.getMaturesAt(),
                                                c.getOpenedAt(),
                                                c.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    PageResult<SavedItem> saved(long user, int page, int size) {
        ActivityApi.page(page, size);
        return PageResult.of(
                concerns.findSaved(user, PageRequest.of(page, size))
                        .map(
                                row ->
                                        new SavedItem(
                                                row.concern().getId().toString(),
                                                row.concern().preview(),
                                                row.concern().getComfortType(),
                                                row.replyCount(),
                                                row.concern().getSavedAt())));
    }

    SaveResult save(long user, long id) {
        var concern = mineForUpdate(user, id);
        concern.save(ConcernRules.now(clock));
        return new SaveResult(concern.getId().toString(), ConcernStatus.SAVED, concern.getSavedAt());
    }

    SaveResult unsave(long user, long id) {
        var concern = mineForUpdate(user, id);
        concern.unsave();
        return new SaveResult(
                concern.getId().toString(), concern.statusAt(ConcernRules.now(clock)), null);
    }

    private Concern mineForUpdate(long user, long id) {
        return concerns.findMineForUpdate(id, user)
                .orElseThrow(() -> ActivityApi.problem(404, "CONCERN_NOT_FOUND"));
    }
}
