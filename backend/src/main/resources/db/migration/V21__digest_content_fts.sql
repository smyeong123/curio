-- Full-text search over digest content. We index a tsvector over the JSON
-- payload as text so users can find a digest by any summary/headline word.
--
-- IMMUTABLE wrapper required because to_tsvector(content::text) is technically
-- STABLE for jsonb; wrapping in jsonb_to_text gives us deterministic input.
CREATE INDEX IF NOT EXISTS idx_digests_content_fts
    ON digests USING GIN (to_tsvector('english', content::text));
