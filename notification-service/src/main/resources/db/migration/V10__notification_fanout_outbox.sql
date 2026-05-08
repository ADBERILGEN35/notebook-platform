CREATE TABLE notification_fanout_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    recipient_user_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(255),
    lock_expires_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_fanout_outbox_status_next
    ON notification_fanout_outbox (status, next_attempt_at);

CREATE INDEX idx_notification_fanout_outbox_recipient_created
    ON notification_fanout_outbox (recipient_user_id, created_at DESC);

CREATE INDEX idx_notification_fanout_outbox_type_created
    ON notification_fanout_outbox (event_type, created_at DESC);
