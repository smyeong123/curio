-- Flyway Migration V8: Add indexes for frequently queried timestamp columns
CREATE INDEX idx_digests_email_sent_at ON digests(email_sent_at) WHERE email_sent_at IS NOT NULL;
CREATE INDEX idx_quiz_attempts_completed_at ON quiz_attempts(completed_at);
