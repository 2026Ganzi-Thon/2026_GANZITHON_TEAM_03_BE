package com.ganzithon.maeum.concern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

class ConcernMySqlIntegrationTest extends ActivityMySqlTestSupport {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 3, 0);
    private static final LocalDateTime MATURES = NOW.plusHours(1);

    @AfterEach
    void clean() {
        clearFixtures("concerns");
    }

    private static Timestamp ts(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private long concern(String comfortType, LocalDateTime openedAt, LocalDateTime savedAt) {
        KeyHolder key = new GeneratedKeyHolder();
        db.update(
                connection -> {
                    PreparedStatement ps =
                            connection.prepareStatement(
                                    "INSERT INTO concerns(user_id,content,comfort_type,matures_at,"
                                            + "opened_at,saved_at,created_at) VALUES(?,?,?,?,?,?,?)",
                                    Statement.RETURN_GENERATED_KEYS);
                    ps.setLong(1, user);
                    ps.setString(2, "고민");
                    ps.setString(3, comfortType);
                    ps.setTimestamp(4, ts(MATURES));
                    ps.setTimestamp(5, ts(openedAt));
                    ps.setTimestamp(6, ts(savedAt));
                    ps.setTimestamp(7, ts(NOW));
                    return ps;
                },
                key);
        return key.getKey().longValue();
    }

    private void reply(long concernId, Long authorId, String sourceType) {
        db.update(
                "INSERT INTO comfort_replies(concern_id,author_id,source_type,content,created_at)"
                        + " VALUES(?,?,?,?,?)",
                concernId,
                authorId,
                sourceType,
                "위로",
                NOW);
    }

    private int replyCount(long concernId) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM comfort_replies WHERE concern_id=?", Integer.class, concernId);
    }

    @Test
    void v9IsApplied() {
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM flyway_schema_history WHERE version='9' AND success=1",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void duplicateReplyFromSameAuthorIsRejected() {
        long id = concern("EMPATHY", null, null);
        reply(id, other, "USER");
        assertThatThrownBy(() -> reply(id, other, "USER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_reply_author");
        assertThat(replyCount(id)).isEqualTo(1);
    }

    @Test
    void onlyOneSystemReplyIsAllowedPerConcern() {
        long id = concern("EMPATHY", null, null);
        reply(id, null, "SYSTEM");
        assertThatThrownBy(() -> reply(id, null, "SYSTEM"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_reply_system");
        assertThat(replyCount(id)).isEqualTo(1);
    }

    @Test
    void systemRepliesOnDifferentConcernsAreIndependent() {
        long first = concern("EMPATHY", null, null);
        long second = concern("ADVICE", null, null);
        reply(first, null, "SYSTEM");
        reply(second, null, "SYSTEM");
        reply(first, other, "USER");
        assertThat(replyCount(first)).isEqualTo(2);
        assertThat(replyCount(second)).isEqualTo(1);
    }

    @Test
    void deletingConcernDeletesReplies() {
        long id = concern("ADVICE", null, null);
        reply(id, other, "USER");
        reply(id, null, "SYSTEM");
        db.update("DELETE FROM concerns WHERE id=?", id);
        assertThat(replyCount(id)).isZero();
    }

    @Test
    void invalidComfortTypeIsRejected() {
        assertThatThrownBy(() -> concern("UNKNOWN", null, null))
                .hasMessageContaining("ck_concern_comfort_type");
    }

    @Test
    void savedWithoutOpenedIsRejected() {
        assertThatThrownBy(() -> concern("EXPERIENCE", null, MATURES))
                .hasMessageContaining("ck_concern_saved_opened");
        concern("EXPERIENCE", MATURES, MATURES.plusMinutes(1));
    }

    @Test
    void openedBeforeMaturityIsRejected() {
        assertThatThrownBy(() -> concern("EMPATHY", MATURES.minusSeconds(1), null))
                .hasMessageContaining("ck_concern_opened_after_matured");
        concern("EMPATHY", MATURES, null);
    }

    @Test
    void savedBeforeOpenedIsRejected() {
        assertThatThrownBy(() -> concern("EMPATHY", MATURES.plusMinutes(5), MATURES.plusMinutes(1)))
                .hasMessageContaining("ck_concern_saved_after_opened");
    }

    @Test
    void invalidSourceTypeAndAuthorCombinationsAreRejected() {
        long id = concern("EMPATHY", null, null);
        // 정의되지 않은 출처는 출처 값 CHECK와 출처·작성자 조합 CHECK 모두에 걸리며,
        // MySQL은 그중 하나의 이름만 알려 주므로 두 제약의 공통 접두사로 확인합니다.
        assertThatThrownBy(() -> reply(id, other, "OTHER"))
                .hasMessageContaining("ck_reply_source_");
        assertThatThrownBy(() -> reply(id, null, "OTHER"))
                .hasMessageContaining("ck_reply_source_");
        assertThatThrownBy(() -> reply(id, null, "USER"))
                .hasMessageContaining("ck_reply_source_author");
        assertThatThrownBy(() -> reply(id, other, "SYSTEM"))
                .hasMessageContaining("ck_reply_source_author");
        assertThat(replyCount(id)).isZero();
    }
}
