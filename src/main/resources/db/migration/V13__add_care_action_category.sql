ALTER TABLE care_actions
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT '기타' AFTER description;
