package com.ganzithon.maeum.concern;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** 고민에 달린 위로입니다. V9의 생성 컬럼 system_concern_id는 DB가 계산하므로 매핑하지 않습니다. */
@Entity
@Table(name = "comfort_replies")
class ComfortReply {
    enum SourceType {
        USER,
        SYSTEM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private long concernId;

    @Column(updatable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 20)
    private SourceType sourceType;

    @Column(nullable = false, updatable = false, length = 500)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ComfortReply() {}

    private ComfortReply(
            long concernId, Long authorId, SourceType sourceType, String content, Instant createdAt) {
        this.concernId = concernId;
        this.authorId = authorId;
        this.sourceType = sourceType;
        this.content = content;
        this.createdAt = createdAt;
    }

    static ComfortReply byUser(long concernId, long authorId, String content, Instant now) {
        return new ComfortReply(concernId, authorId, SourceType.USER, content, now);
    }

    static ComfortReply bySystem(long concernId, String content, Instant now) {
        return new ComfortReply(concernId, null, SourceType.SYSTEM, content, now);
    }

    Long getId() {
        return id;
    }

    String getContent() {
        return content;
    }

    boolean isFromSystem() {
        return sourceType == SourceType.SYSTEM;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
