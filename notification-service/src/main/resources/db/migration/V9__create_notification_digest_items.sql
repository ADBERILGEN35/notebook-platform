CREATE TABLE IF NOT EXISTS notification_digest_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    source_notification_id UUID,
    email_notification_id UUID,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    action_url VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_digest_items_user_status_scheduled
    ON notification_digest_items (user_id, status, scheduled_for);

CREATE INDEX IF NOT EXISTS idx_notification_digest_items_status_scheduled
    ON notification_digest_items (status, scheduled_for);
