CREATE TABLE notification_audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100),
    aggregate_id UUID,
    request_id VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_audit_events_created_at ON notification_audit_events(created_at);
CREATE INDEX idx_notification_audit_events_event_type ON notification_audit_events(event_type);
CREATE INDEX idx_notification_audit_events_aggregate ON notification_audit_events(aggregate_type, aggregate_id);
CREATE INDEX idx_notification_audit_events_request_id ON notification_audit_events(request_id);
