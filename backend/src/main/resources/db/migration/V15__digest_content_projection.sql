-- V15: digest content projection table (additive, backfill-safe).
-- The existing digests.content JSONB column stays as source of truth for now.
-- This table mirrors the payload so NewsService / AdminDigestService can
-- cheaply SELECT digest metadata (id, user_id, generated_at) without
-- dragging large JSONB bodies into memory. A follow-up migration (V16)
-- can flip NewsService reads to this table and drop the legacy column.
--
-- Rollout plan:
--   1. Apply V15 (creates shadow table, empty).
--   2. Run DigestContentBackfillJob (one-off) to populate from digests.content.
--   3. Switch code paths to read/write digest_bodies.
--   4. Drop digests.content in a subsequent migration after verifying.

CREATE TABLE IF NOT EXISTS digest_bodies (
    digest_id UUID PRIMARY KEY REFERENCES digests(id) ON DELETE CASCADE,
    content JSONB NOT NULL,
    content_bytes INTEGER GENERATED ALWAYS AS (octet_length(content::text)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Let PostgreSQL TOAST the JSONB more aggressively. EXTENDED is already the
-- default for JSONB, but call it out explicitly for documentation.
ALTER TABLE digest_bodies ALTER COLUMN content SET STORAGE EXTENDED;

CREATE INDEX IF NOT EXISTS idx_digest_bodies_size
    ON digest_bodies(content_bytes)
    WHERE content_bytes > 32768;

COMMENT ON TABLE digest_bodies IS
    'Shadow table for digest JSONB bodies; see V15 header comment for rollout plan.';
