CREATE TABLE IF NOT EXISTS email_notifications (
    id UUID PRIMARY KEY,
    type varchar(80) NOT NULL,
    recipient_email varchar(320) NOT NULL,
    subject varchar(300) NOT NULL,
    body_text text,
    body_html text,
    status varchar(40) NOT NULL,
    provider varchar(80),
    provider_message_id varchar(200),
    idempotency_key varchar(300),
    attempt_count int NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    last_error text,
    sent_at timestamptz,
    failed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_email_notifications_idempotency_key
    ON email_notifications (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_email_notifications_status_next_attempt
    ON email_notifications (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_email_notifications_recipient_email
    ON email_notifications (recipient_email);
CREATE INDEX IF NOT EXISTS idx_email_notifications_type
    ON email_notifications (type);
CREATE INDEX IF NOT EXISTS idx_email_notifications_created_at
    ON email_notifications (created_at);
