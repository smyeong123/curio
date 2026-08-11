-- Flyway Migration V24: Create email verification codes table
-- Backs email-based 2FA: after a successful email/password login, a short-lived
-- one-time code is emailed to the user and must be entered to complete sign-in.
-- The challenge id and the code are both stored hashed (SHA-256), never plaintext,
-- mirroring refresh_tokens (V9) and password_reset_tokens (V7).
CREATE TABLE email_verification_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_hash VARCHAR(255) UNIQUE NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    attempts_remaining INTEGER NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_email_verification_codes_user_id ON email_verification_codes(user_id);
CREATE INDEX idx_email_verification_codes_expires_at ON email_verification_codes(expires_at);
