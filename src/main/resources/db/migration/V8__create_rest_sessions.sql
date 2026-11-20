CREATE TABLE rest_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, planned_duration_seconds INT NOT NULL,
    started_at DATETIME(6) NOT NULL, scheduled_end_at DATETIME(6) NOT NULL, ended_at DATETIME(6),
    status VARCHAR(20) NOT NULL, after_memo VARCHAR(500),
    CONSTRAINT fk_rest_user FOREIGN KEY(user_id) REFERENCES users(id),
    INDEX idx_rest_user_time(user_id,started_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
