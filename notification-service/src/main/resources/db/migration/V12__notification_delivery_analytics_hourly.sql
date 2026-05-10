-- Faz 81: privacy-safe hourly aggregates (no userId, workspaceId, message/body, email)
CREATE TABLE notification_delivery_analytics_hourly (
    id UUID PRIMARY KEY,
    bucket_start TIMESTAMPTZ NOT NULL,
    source_service VARCHAR(64) NOT NULL DEFAULT 'notification-service',
    notification_type VARCHAR(128) NOT NULL DEFAULT '',
    channel VARCHAR(32) NOT NULL DEFAULT '',
    severity VARCHAR(32) NOT NULL DEFAULT '',
    event_kind VARCHAR(64) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notification_delivery_analytics_dims UNIQUE (
        bucket_start,
        notification_type,
        channel,
        severity,
        event_kind
    )
);

CREATE INDEX idx_notification_delivery_analytics_bucket ON notification_delivery_analytics_hourly (bucket_start);
