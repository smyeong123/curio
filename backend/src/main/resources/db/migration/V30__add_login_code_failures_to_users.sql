-- Per-ACCOUNT budget for wrong email-2FA codes.
--
-- The per-challenge attemptsRemaining counter (V24-era email_verification_codes)
-- reset on every new password login, so anyone holding the password could
-- re-login and get a fresh 5 guesses indefinitely — the "lock" was cosmetic.
-- This counter survives new challenges: once it reaches the configured cap
-- (auth.email-verification.max-account-failures, default 10) no new code is
-- issued until the owner resets their password. A successful verification
-- or a password reset sets it back to 0.
ALTER TABLE users ADD COLUMN login_code_failures INTEGER NOT NULL DEFAULT 0;
