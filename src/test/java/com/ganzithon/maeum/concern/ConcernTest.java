package com.ganzithon.maeum.concern;

import static org.junit.jupiter.api.Assertions.*;

import com.ganzithon.maeum.global.activity.ActivityApi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;

/** Concern 엔티티의 상태 계산과 위로·개봉·보관 규칙을 DB 없이 검증합니다. */
class ConcernTest {
    private static final long OWNER = 1L;
    private static final long OTHER = 2L;
    private static final Instant MATURES = Instant.parse("2026-11-11T03:00:00Z");
    private static final Instant BEFORE = MATURES.minusNanos(1000);
    private static final Instant AFTER = MATURES.plusSeconds(60);

    private static Concern concern(String content) {
        return new Concern(OWNER, "제목", content, ComfortType.ADVICE, MATURES, MATURES.minusSeconds(86400));
    }

    private static Concern concern() {
        return concern("고민");
    }

    /** 오류 상태와 코드는 공통 클래스의 패키지 전용 필드라 리플렉션으로 읽습니다. */
    private static void assertProblem(int status, String code, Executable action) throws Exception {
        var problem = assertThrows(ActivityApi.Problem.class, action);
        var statusField = ActivityApi.Problem.class.getDeclaredField("status");
        var codeField = ActivityApi.Problem.class.getDeclaredField("code");
        statusField.setAccessible(true);
        codeField.setAccessible(true);
        assertEquals(status, statusField.getInt(problem));
        assertEquals(code, codeField.get(problem));
    }

    @Test
    void keepsTitle() {
        assertEquals("제목", concern().getTitle());
    }

    // ---- 상태 계산 ----

    @Test
    void statusIsMaturingBeforeMaturesAtAndMaturedFromIt() {
        var c = concern();
        assertEquals(ConcernStatus.MATURING, c.statusAt(BEFORE));
        assertEquals(ConcernStatus.MATURED, c.statusAt(MATURES));
        assertEquals(ConcernStatus.MATURED, c.statusAt(AFTER));
    }

    @Test
    void statusIsOpenedAfterOpenAndSavedAfterSave() {
        var c = concern();
        c.open(MATURES);
        assertEquals(ConcernStatus.OPENED, c.statusAt(AFTER));
        c.save(AFTER);
        assertEquals(ConcernStatus.SAVED, c.statusAt(AFTER));
        c.unsave();
        assertEquals(ConcernStatus.OPENED, c.statusAt(AFTER));
    }

    // ---- 위로 가능 여부 ----

    @Test
    void othersCanReplyOnlyWhileMaturing() throws Exception {
        var c = concern();
        assertDoesNotThrow(() -> c.checkReplyable(OTHER, BEFORE));
        assertProblem(409, "CONCERN_REPLY_CLOSED", () -> c.checkReplyable(OTHER, MATURES));
        assertProblem(409, "CONCERN_REPLY_CLOSED", () -> c.checkReplyable(OTHER, AFTER));
    }

    @Test
    void ownerCannotReplyEvenWhileMaturing() throws Exception {
        assertProblem(403, "REPLY_OWN_CONCERN_FORBIDDEN", () -> concern().checkReplyable(OWNER, BEFORE));
    }

    @Test
    void openedConcernIsClosedForReplies() throws Exception {
        var c = concern();
        c.open(MATURES);
        assertProblem(409, "CONCERN_REPLY_CLOSED", () -> c.checkReplyable(OTHER, BEFORE));
    }

    // ---- 개봉 ----

    @Test
    void openBeforeMaturityIs409AndKeepsUnopened() throws Exception {
        var c = concern();
        assertProblem(409, "CONCERN_NOT_MATURED", () -> c.open(BEFORE));
        assertNull(c.getOpenedAt());
    }

    @Test
    void firstOpenReturnsTrueAndRepeatKeepsFirstOpenedAt() {
        var c = concern();
        assertTrue(c.open(MATURES));
        assertEquals(MATURES, c.getOpenedAt());
        assertFalse(c.open(AFTER));
        assertEquals(MATURES, c.getOpenedAt());
    }

    // ---- 보관 ----

    @Test
    void saveBeforeOpenIs409() throws Exception {
        var c = concern();
        assertProblem(409, "CONCERN_NOT_OPENED", () -> c.save(AFTER));
        assertNull(c.getSavedAt());
    }

    @Test
    void repeatSaveKeepsFirstSavedAt() {
        var c = concern();
        c.open(MATURES);
        c.save(AFTER);
        c.save(AFTER.plusSeconds(60));
        assertEquals(AFTER, c.getSavedAt());
    }

    @Test
    void unsaveClearsSavedAtAndIsRepeatable() {
        var c = concern();
        c.open(MATURES);
        c.save(AFTER);
        c.unsave();
        c.unsave();
        assertNull(c.getSavedAt());
        assertEquals(MATURES, c.getOpenedAt());
    }

    @Test
    void unsaveOnUnopenedKeepsComputedStatus() {
        var c = concern();
        c.unsave();
        assertEquals(ConcernStatus.MATURING, c.statusAt(BEFORE));
    }

    // ---- 미리보기 ----

    @Test
    void previewCutsAt100CodePoints() {
        assertEquals("짧은 고민", concern("짧은 고민").preview());
        String emoji = "😀".repeat(101);
        assertEquals("😀".repeat(100), concern(emoji).preview());
    }

    @Test
    void ownership() {
        assertTrue(concern().isOwnedBy(OWNER));
        assertFalse(concern().isOwnedBy(OTHER));
    }
}
