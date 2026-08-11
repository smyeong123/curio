-- Add GIN index on content->'generatedFor' for efficient topic-based filtering
CREATE INDEX IF NOT EXISTS idx_digests_content_generated_for
    ON digests USING GIN ((content->'generatedFor'));
