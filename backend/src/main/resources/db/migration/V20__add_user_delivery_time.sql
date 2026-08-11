ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS timezone     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS delivery_hour INTEGER;

-- Enforce a sane range; NULL means "use the default 8:00 UTC".
ALTER TABLE user_preferences
    ADD CONSTRAINT chk_delivery_hour_range CHECK (delivery_hour IS NULL OR (delivery_hour BETWEEN 0 AND 23));

-- Hot path for EmailSendJob: "give me all users whose local time is now"
CREATE INDEX IF NOT EXISTS idx_user_preferences_delivery
    ON user_preferences (delivery_hour, timezone);
