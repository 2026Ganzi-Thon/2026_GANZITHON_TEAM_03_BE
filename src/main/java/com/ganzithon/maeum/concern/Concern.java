package com.ganzithon.maeum.concern;

import com.ganzithon.maeum.global.activity.ActivityApi;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** 고민 한 건입니다. 상태 계산과 위로·개봉·보관 규칙을 함께 둡니다. */
@Entity
@Table(name = "concerns")
class Concern {
    private static final int PREVIEW_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private long userId;

    @Column(nullable = false, updatable = false, length = 1000)
    private String content;

    // MySQL에서 Hibernate 기본값은 ENUM 컬럼이라, V9의 VARCHAR 컬럼과 맞게 지정합니다.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 30)
    private ComfortType comfortType;

    @Column(nullable = false, updatable = false)
    private Instant maturesAt;

    private Instant openedAt;

    private Instant savedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Concern() {}

    Concern(long userId, String content, ComfortType comfortType, Instant maturesAt, Instant createdAt) {
        this.userId = userId;
        this.content = content;
        this.comfortType = comfortType;
        this.maturesAt = maturesAt;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    String getContent() {
        return content;
    }

    ComfortType getComfortType() {
        return comfortType;
    }

    Instant getMaturesAt() {
        return maturesAt;
    }

    Instant getOpenedAt() {
        return openedAt;
    }

    Instant getSavedAt() {
        return savedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    boolean isOwnedBy(long user) {
        return userId == user;
    }

    ConcernStatus statusAt(Instant now) {
        if (savedAt != null) return ConcernStatus.SAVED;
        if (openedAt != null) return ConcernStatus.OPENED;
        return now.isBefore(maturesAt) ? ConcernStatus.MATURING : ConcernStatus.MATURED;
    }

    /** 앞에서부터 최대 100자(코드 포인트 기준)를 돌려줍니다. */
    String preview() {
        int end =
                content.offsetByCodePoints(
                        0, Math.min(PREVIEW_LENGTH, content.codePointCount(0, content.length())));
        return content.substring(0, end);
    }

    /** 남의 고민이고 숙성 중일 때만 위로할 수 있습니다. */
    void checkReplyable(long user, Instant now) {
        if (isOwnedBy(user)) throw ActivityApi.problem(403, "REPLY_OWN_CONCERN_FORBIDDEN");
        if (openedAt != null || !now.isBefore(maturesAt))
            throw ActivityApi.problem(409, "CONCERN_REPLY_CLOSED");
    }

    /** 숙성된 고민을 엽니다. 처음 연 경우에만 true를 돌려줍니다. */
    boolean open(Instant now) {
        if (openedAt != null) return false;
        if (now.isBefore(maturesAt)) throw ActivityApi.problem(409, "CONCERN_NOT_MATURED");
        openedAt = now;
        return true;
    }

    /** 개봉한 고민을 보관합니다. 이미 보관했다면 처음 보관 시각을 유지합니다. */
    void save(Instant now) {
        if (openedAt == null) throw ActivityApi.problem(409, "CONCERN_NOT_OPENED");
        if (savedAt == null) savedAt = now;
    }

    void unsave() {
        savedAt = null;
    }
}
