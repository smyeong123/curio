-- Flyway Migration V6: Track provider message id for webhook reconciliation
ALTER TABLE digests
    ADD COLUMN email_provider_id VARCHAR(255);

CREATE UNIQUE INDEX idx_digests_email_provider_id ON digests(email_provider_id);
