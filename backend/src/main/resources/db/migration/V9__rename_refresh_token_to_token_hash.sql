-- Flyway Migration V9: Rename refresh_tokens.token to token_hash
-- Refresh tokens are now SHA-256 hashed before storage (like password_reset_tokens).
-- Existing plain-text tokens are invalidated; users will need to re-authenticate.

DROP INDEX IF EXISTS idx_refresh_tokens_token;
ALTER TABLE refresh_tokens RENAME COLUMN token TO token_hash;
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- Clear existing plain-text tokens since they won't match hashed lookups
TRUNCATE TABLE refresh_tokens;
