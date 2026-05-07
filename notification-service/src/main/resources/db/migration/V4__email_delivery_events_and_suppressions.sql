ALTER TABLE email_notifications
    ADD COLUMN IF NOT EXISTS delivery_status varchar(40),
    ADD COLUMN IF NOT EXISTS delivered_at timestamptz,
    ADD COLUMN IF NOT EXISTS bounced_at timestamptz,
    ADD COLUMN IF NOT EXISTS complained_at timestamptz,
    ADD COLUMN IF NOT EXISTS suppressed_at timestamptz,
    ADD COLUMN IF NOT EXISTS provider_event_id varchar(200),
    ADD COLUMN IF NOT EXISTS provider_event_payload jsonb;

CREATE INDEX IF NOT EXISTS idx_email_notifications_provider_message_id
    ON email_notifications (provider, provider_message_id)
    WHERE provider_message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS email_provider_events (
    id UUID PRIMARY KEY,
    provider varchar(80) NOT NULL,
    provider_event_id varchar(200) NOT NULL,
    provider_message_id varchar(200),
    event_type varchar(40) NOT NULL,
    recipient_email varchar(320),
    occurred_at timestamptz NOT NULL,
    payload jsonb,
    created_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_email_provider_events_provider_event
    ON email_provider_events (provider, provider_event_id);
CREATE INDEX IF NOT EXISTS idx_email_provider_events_message
    ON email_provider_events (provider, provider_message_id);

CREATE TABLE IF NOT EXISTS email_suppressions (
    id UUID PRIMARY KEY,
    email varchar(320) NOT NULL,
    reason varchar(40) NOT NULL,
    provider varchar(80),
    provider_event_id varchar(200),
    source varchar(80) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_email_suppressions_email_lower
    ON email_suppressions (lower(email));
CREATE INDEX IF NOT EXISTS idx_email_suppressions_reason
    ON email_suppressions (reason);
