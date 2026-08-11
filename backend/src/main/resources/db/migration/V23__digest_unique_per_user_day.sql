-- Enforce one digest per user per UTC calendar day at the database level.
-- This is an idempotency backstop: the application already guards with an
-- existence check (NewsService) and the email send is now idempotent, but a
-- ShedLock lapse or an admin-triggered run overlapping the scheduled job could
-- still race the check-then-insert. The unique index makes a duplicate insert
-- fail fast instead of silently double-generating.
--
-- ⚠️ DEPLOY NOTE: validate on a staging copy of prod data before rolling out.
-- Flyway is forward-only; if this migration errors, the backend crash-loops.
-- It was authored without a live Postgres to test against.

-- `(ts AT TIME ZONE 'UTC')` on a timestamptz is only STABLE, so it cannot be used
-- directly in an index expression. Pinning the zone to the literal 'UTC' makes the
-- result genuinely deterministic, so wrapping it in an IMMUTABLE function is sound
-- and lets Postgres build an index on it. generated_at is stored as UTC (the JVM
-- runs UTC and Hibernate binds with jdbc.time_zone=UTC), matching the app's
-- LocalDate-based "today" check.
CREATE OR REPLACE FUNCTION curio_utc_date(ts timestamptz)
    RETURNS date
    LANGUAGE sql
    IMMUTABLE
AS $$ SELECT (ts AT TIME ZONE 'UTC')::date $$;

-- Remove any pre-existing same-user/same-UTC-day duplicates, keeping the most
-- recent (tie-break on id). The application prevents these, so this is normally a
-- no-op; it exists only so the unique index can be created safely. Dropped
-- duplicate digests cascade to their quizzes/quiz_attempts (ON DELETE CASCADE) —
-- acceptable, as only the superseded copy of a duplicated day is removed.
DELETE FROM digests d
USING digests d2
WHERE d.generated_at IS NOT NULL
  AND d2.generated_at IS NOT NULL
  AND d.user_id = d2.user_id
  AND curio_utc_date(d.generated_at) = curio_utc_date(d2.generated_at)
  AND (d.generated_at < d2.generated_at
       OR (d.generated_at = d2.generated_at AND d.id < d2.id));

CREATE UNIQUE INDEX idx_digests_user_utc_day
    ON digests (user_id, curio_utc_date(generated_at))
    WHERE generated_at IS NOT NULL;
