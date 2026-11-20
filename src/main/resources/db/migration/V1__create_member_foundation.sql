CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contact_email VARCHAR(255) NOT NULL,
    nickname VARCHAR(30) NULL,
    profile_image_url VARCHAR(1000) NULL,
    introduction VARCHAR(200) NULL,
    age_group VARCHAR(30) NULL,
    region_code VARCHAR(30) NULL,
    onboarding_completed_at DATETIME(6) NULL,
    profile_completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_nickname UNIQUE (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE local_credentials (
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified_at DATETIME(6) NULL,
    password_changed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_local_credentials_email UNIQUE (email),
    CONSTRAINT fk_local_credentials_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_local_credentials_normalized_email CHECK (
        BINARY email = BINARY LOWER(TRIM(email))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
