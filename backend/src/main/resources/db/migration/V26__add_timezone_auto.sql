-- Whether the user's delivery timezone auto-follows their device (true) or is
-- pinned to a fixed zone they chose in Settings (false). Existing users default to
-- auto so their digest tracks their current location without any action.
ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS timezone_auto BOOLEAN NOT NULL DEFAULT TRUE;
