-- Faz 84: legal hold / retention governance (notification retention scope only)
CREATE TABLE notification_legal_holds (
    id UUID PRIMARY KEY,
    hold_key VARCHAR(200) NOT NULL,
    scope VARCHAR(64) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_by_email VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL,
    released_by_user_id UUID,
    released_at TIMESTAMPTZ,
    release_reason TEXT,
    expires_at TIMESTAMPTZ,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_notification_legal_holds_hold_key UNIQUE (hold_key)
);

CREATE INDEX idx_notification_legal_holds_status ON notification_legal_holds (status);
CREATE INDEX idx_notification_legal_holds_scope_status ON notification_legal_holds (scope, status);
