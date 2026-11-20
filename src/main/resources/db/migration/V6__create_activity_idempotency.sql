CREATE TABLE api_idempotency_requests (
    user_id BIGINT NOT NULL, request_key VARCHAR(36) NOT NULL, request_hash CHAR(64) NOT NULL,
    response_status INT NOT NULL, response_body TEXT NOT NULL, expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY(user_id,request_key), CONSTRAINT fk_idem_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
