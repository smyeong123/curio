-- Flyway Migration V32: sign-up email confirmation.
--
-- register() used to auto-verify every address (MVP). A new password account is
-- now created with email_verified = false and gets no session until the owner
-- opens the link mailed to them. Only the SHA-256 hash of the link token is
-- stored (TokenHasher), like password_reset_tokens (V7). Rows are single-use
-- (used_at) and reaped by ExpiredAuthRowReaper once past expires_at.
CREATE TABLE email_confirmation_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_email_confirmation_tokens_user_id ON email_confirmation_tokens(user_id);
CREATE INDEX idx_email_confirmation_tokens_expires_at ON email_confirmation_tokens(expires_at);

-- When the owner last PROVED inbox ownership (opened a confirmation link or
-- completed a password reset). Deliberately NOT backfilled: every existing
-- password account has email_verified = true only because the old register()
-- auto-verified it, which proves nothing. email_verified keeps gating login (so
-- legacy users keep signing in); the Google-merge anti-squat rule trusts only
-- this column, so a legacy password row is still neutralized when the real
-- owner signs in with Google.
ALTER TABLE users ADD COLUMN email_confirmed_at TIMESTAMP WITH TIME ZONE NULL;
