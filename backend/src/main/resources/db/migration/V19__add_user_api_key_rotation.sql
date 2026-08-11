ALTER TABLE user_api_keys
    ADD COLUMN IF NOT EXISTS rotated_at TIMESTAMP;

-- Treat existing keys as freshly rotated at their creation time so the 90-day
-- stale warning does not fire retroactively for every user.
UPDATE user_api_keys
   SET rotated_at = created_at
 WHERE rotated_at IS NULL;
