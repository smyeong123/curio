-- Enforce one account per real mailbox regardless of case.
--
-- The users.email UNIQUE constraint (V1) is a plain case-sensitive btree, so
-- Victim@x.com and victim@x.com were two distinct rows — which defeated the
-- Google-merge anti-squatting defense (its lookup is case-sensitive). Code now
-- normalizes email to trimmed-lowercase at every boundary; this migration makes
-- the invariant hold at the DB level even if a future path forgets.

-- 1. Fold any existing mixed-case addresses down to lowercase. If two rows
--    already collide case-insensitively, keep the oldest (lowest id via
--    created_at) and leave the collision to surface when the unique index is
--    created — expected to be empty on current data.
UPDATE users
SET email = lower(trim(email))
WHERE email <> lower(trim(email));

-- 2. Case-insensitive uniqueness backstop. A functional unique index on
--    lower(email) rejects any future case-variant duplicate at the DB layer.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (lower(email));
