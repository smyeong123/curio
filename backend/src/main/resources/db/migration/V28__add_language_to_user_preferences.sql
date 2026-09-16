-- Edition the user's digest, quiz and daily email are written in: 'en' | 'ko'
-- (lowercase BCP-47 base code, the same value the frontend locale uses).
-- Defaults to English so every existing reader keeps getting exactly what
-- they get today; the Settings "Edition" selector and onboarding set it.
ALTER TABLE user_preferences
    ADD COLUMN language VARCHAR(8) NOT NULL DEFAULT 'en';

ALTER TABLE user_preferences
    ADD CONSTRAINT user_preferences_language_check CHECK (language IN ('en', 'ko'));
