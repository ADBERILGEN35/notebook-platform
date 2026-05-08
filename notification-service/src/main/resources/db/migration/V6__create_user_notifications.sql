CREATE TABLE user_notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    workspace_id UUID,
    type VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(50) NOT NULL,
    action_url VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(255),
    read_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_notifications_recipient_read_created
    ON user_notifications(recipient_user_id, read_at, created_at DESC);
CREATE INDEX idx_user_notifications_recipient_archived
    ON user_notifications(recipient_user_id, archived_at);
CREATE INDEX idx_user_notifications_workspace_id
    ON user_notifications(workspace_id);
CREATE INDEX idx_user_notifications_type
    ON user_notifications(type);
CREATE INDEX idx_user_notifications_created_at
    ON user_notifications(created_at DESC);

CREATE UNIQUE INDEX ux_user_notifications_recipient_idempotency
    ON user_notifications(recipient_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
