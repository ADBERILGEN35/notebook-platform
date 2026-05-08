CREATE TABLE IF NOT EXISTS user_notification_delivery_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    email_digest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_digest_frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
