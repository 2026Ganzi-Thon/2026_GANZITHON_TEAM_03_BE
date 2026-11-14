package com.ganzithon.maeum.concern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

class ConcernMySqlIntegrationTest extends ActivityMySqlTestSupport {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 3, 0);

    @AfterEach
    void clean() {
        clearFixtures("concerns");
    }

    private long concern(String comfortType, LocalDateTime openedAt, LocalDateTime savedAt) {
        db.update(
                "INSERT INTO concerns(user_id,content,comfort_type,matures_at,opened_at,saved_at,created_at)"
                        + " VALUES(?,?,?,?,?,?,?)",
                user,
                "고민",
                comfortType,
                NOW.plusHours(1),
                openedAt,
                savedAt,
                NOW);
        return db.queryForObject("SELECT MAX(id) FROM concerns WHERE user_id=?", Long.class, user);
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
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(replyCount(id)).isEqualTo(1);
    }

    @Test
    void systemRepliesWithNullAuthorAreAllowedMultipleTimes() {
        long id = concern("EMPATHY", null, null);
        reply(id, null, "SYSTEM");
        reply(id, null, "SYSTEM");
        assertThat(replyCount(id)).isEqualTo(2);
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
        assertThatThrownBy(() -> concern("EXPERIENCE", null, NOW))
                .hasMessageContaining("ck_concern_saved_opened");
        concern("EXPERIENCE", NOW, NOW);
    }

    @Test
    void invalidSourceTypeAndAuthorCombinationsAreRejected() {
        long id = concern("EMPATHY", null, null);
        assertThatThrownBy(() -> reply(id, other, "OTHER"))
                .hasMessageContaining("Check constraint");
        assertThatThrownBy(() -> reply(id, null, "USER"))
                .hasMessageContaining("ck_reply_source_author");
        assertThatThrownBy(() -> reply(id, other, "SYSTEM"))
                .hasMessageContaining("ck_reply_source_author");
        assertThat(replyCount(id)).isZero();
    }
}
