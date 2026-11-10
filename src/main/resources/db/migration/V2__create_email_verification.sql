CREATE TABLE auth_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    used_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_auth_verifications_token UNIQUE (token_hash),
    INDEX ix_auth_verifications_email_purpose (email, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- 이메일/IP 제한은 원문 대신 해시 키로 관리합니다. 잠금으로 다중 요청을 직렬화합니다.
CREATE TABLE email_verification_limits (
    limit_key CHAR(64) NOT NULL PRIMARY KEY,
    window_started_at DATETIME(6) NOT NULL,
    request_count INT NOT NULL,
    last_sent_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
