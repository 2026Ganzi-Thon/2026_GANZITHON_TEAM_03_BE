ALTER TABLE auth_verifications
    ADD COLUMN user_id BIGINT NULL,
    ADD CONSTRAINT fk_auth_verifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
