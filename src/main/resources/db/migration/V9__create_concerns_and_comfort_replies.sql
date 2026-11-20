CREATE TABLE concerns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, content VARCHAR(1000) NOT NULL,
    comfort_type VARCHAR(30) NOT NULL, matures_at DATETIME(6) NOT NULL, opened_at DATETIME(6) NULL,
    saved_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_concern_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT ck_concern_comfort_type CHECK(comfort_type IN ('EMPATHY','EXPERIENCE','ADVICE')),
    CONSTRAINT ck_concern_saved_opened CHECK(saved_at IS NULL OR opened_at IS NOT NULL),
    CONSTRAINT ck_concern_opened_after_matured CHECK(opened_at IS NULL OR opened_at >= matures_at),
    CONSTRAINT ck_concern_saved_after_opened CHECK(saved_at IS NULL OR saved_at >= opened_at),
    INDEX idx_concern_user_time(user_id,created_at,id),
    INDEX idx_concern_user_saved(user_id,saved_at,id),
    INDEX idx_concern_matures(matures_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE comfort_replies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, concern_id BIGINT NOT NULL, author_id BIGINT NULL,
    source_type VARCHAR(20) NOT NULL, content VARCHAR(500) NOT NULL, created_at DATETIME(6) NOT NULL,
    system_concern_id BIGINT GENERATED ALWAYS AS (IF(source_type='SYSTEM', concern_id, NULL)) VIRTUAL,
    CONSTRAINT fk_reply_concern FOREIGN KEY(concern_id) REFERENCES concerns(id) ON DELETE CASCADE,
    CONSTRAINT fk_reply_author FOREIGN KEY(author_id) REFERENCES users(id),
    CONSTRAINT uq_reply_author UNIQUE(concern_id,author_id),
    CONSTRAINT uq_reply_system UNIQUE(system_concern_id),
    CONSTRAINT ck_reply_source_type CHECK(source_type IN ('USER','SYSTEM')),
    CONSTRAINT ck_reply_source_author CHECK((source_type='USER' AND author_id IS NOT NULL) OR (source_type='SYSTEM' AND author_id IS NULL)),
    INDEX idx_reply_author(author_id),
    INDEX idx_reply_concern_time(concern_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
