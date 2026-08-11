-- Flyway Migration V4: Create digests table
CREATE TABLE digests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content JSONB NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    email_sent_at TIMESTAMP WITH TIME ZONE,
    email_opened_at TIMESTAMP WITH TIME ZONE,
    email_clicked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_digests_user_id ON digests(user_id);
CREATE INDEX idx_digests_generated_at ON digests(generated_at);
