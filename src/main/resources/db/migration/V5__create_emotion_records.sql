CREATE TABLE emotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE, name VARCHAR(30) NOT NULL,
    display_order INT NOT NULL, active BOOLEAN NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE emotion_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, record_date DATE NOT NULL,
    memo VARCHAR(1000), created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_record_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT uq_record_day UNIQUE(user_id,record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE emotion_record_emotions (
    emotion_record_id BIGINT NOT NULL, emotion_id BIGINT NOT NULL, PRIMARY KEY(emotion_record_id,emotion_id),
    CONSTRAINT fk_choice_record FOREIGN KEY(emotion_record_id) REFERENCES emotion_records(id) ON DELETE CASCADE,
    CONSTRAINT fk_choice_emotion FOREIGN KEY(emotion_id) REFERENCES emotions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
