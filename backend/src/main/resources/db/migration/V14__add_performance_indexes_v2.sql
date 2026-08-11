-- Composite index for user digest queries (used in getDigests, EmailSendJob)
CREATE INDEX IF NOT EXISTS idx_digests_user_generated
    ON digests(user_id, generated_at DESC);

-- Partial index for email sent tracking (used in admin stats)
CREATE INDEX IF NOT EXISTS idx_digests_email_sent_at
    ON digests(email_sent_at) WHERE email_sent_at IS NOT NULL;

-- Partial index for finding unsent digests (used in EmailSendJob)
CREATE INDEX IF NOT EXISTS idx_digests_unsent
    ON digests(user_id, generated_at DESC) WHERE email_sent_at IS NULL;
