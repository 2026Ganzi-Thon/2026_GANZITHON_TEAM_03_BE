CREATE TABLE care_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, description VARCHAR(500),
    reward_amount INT NOT NULL CHECK(reward_amount>=0), display_order INT NOT NULL, active BOOLEAN NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE care_completions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, care_action_id BIGINT NOT NULL,
    action_date DATE NOT NULL, reward_amount INT NOT NULL CHECK(reward_amount>=0),
    completed_at DATETIME(6) NOT NULL, cancelled_at DATETIME(6),
    CONSTRAINT fk_completion_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_completion_action FOREIGN KEY(care_action_id) REFERENCES care_actions(id),
    CONSTRAINT uq_completion_day UNIQUE(user_id,care_action_id,action_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE wool_wallets (
    user_id BIGINT PRIMARY KEY, balance INT NOT NULL CHECK(balance>=0), updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_wallet_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE wool_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, amount INT NOT NULL,
    transaction_type VARCHAR(30) NOT NULL, care_completion_id BIGINT, reverses_transaction_id BIGINT UNIQUE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_wool_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_wool_completion FOREIGN KEY(care_completion_id) REFERENCES care_completions(id),
    CONSTRAINT fk_wool_reversal FOREIGN KEY(reverses_transaction_id) REFERENCES wool_transactions(id),
    INDEX idx_wool_user_time(user_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
