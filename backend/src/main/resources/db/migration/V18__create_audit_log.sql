CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGSERIAL PRIMARY KEY,
    actor_id     UUID,
    actor_email  VARCHAR(255),
    action       VARCHAR(128) NOT NULL,
    target_type  VARCHAR(64),
    target_id    VARCHAR(255),
    request_id   VARCHAR(128),
    metadata     JSONB,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_actor_id   ON audit_log (actor_id);
CREATE INDEX idx_audit_log_action     ON audit_log (action);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);
