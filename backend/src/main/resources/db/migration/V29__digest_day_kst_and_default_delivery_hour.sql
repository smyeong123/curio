-- One digest per user per DIGEST DAY instead of per UTC calendar day.
--
-- Digests are now generated once a day at 05:00 Korea time and sent to every
-- reader at their own local delivery hour (see com.curio.shared.time.DigestDay).
-- A digest day runs from 05:00 KST to the next 05:00 KST and is named by its
-- Korean calendar date. This replaces V23's UTC-day index so the database
-- backstop agrees with the application's duplicate check.
--
-- Asia/Seoul has no DST and the zone is a literal, so the function is genuinely
-- deterministic and safe to mark IMMUTABLE for use in an index (same reasoning
-- as V23's curio_utc_date).
CREATE OR REPLACE FUNCTION curio_digest_day(ts timestamptz)
    RETURNS date
    LANGUAGE sql
    IMMUTABLE
AS $$ SELECT ((ts AT TIME ZONE 'Asia/Seoul') - interval '5 hours')::date $$;

-- Two digests from different UTC days can share a digest day (e.g. 06:00 UTC and
-- 00:00 UTC the next day both fall in one 05:00–05:00 KST window). Keep the most
-- recent of each pair, as V23 did; the superseded copy's quiz and attempts go
-- with it (ON DELETE CASCADE).
DELETE FROM digests d
USING digests d2
WHERE d.generated_at IS NOT NULL
  AND d2.generated_at IS NOT NULL
  AND d.user_id = d2.user_id
  AND curio_digest_day(d.generated_at) = curio_digest_day(d2.generated_at)
  AND (d.generated_at < d2.generated_at
       OR (d.generated_at = d2.generated_at AND d.id < d2.id));

DROP INDEX IF EXISTS idx_digests_user_utc_day;

CREATE UNIQUE INDEX idx_digests_user_digest_day
    ON digests (user_id, curio_digest_day(generated_at))
    WHERE generated_at IS NOT NULL;

-- The default delivery hour moves from 08:00 to 06:00 local. Onboarding always
-- saved 8, so 8 cannot be told apart from an explicit choice; per the product
-- decision (2026-10-01) every 08:00 reader moves to 06:00.
UPDATE user_preferences SET delivery_hour = 6 WHERE delivery_hour = 8;
