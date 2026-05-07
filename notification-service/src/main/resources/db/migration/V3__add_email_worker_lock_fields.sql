ALTER TABLE email_notifications
    ADD COLUMN IF NOT EXISTS locked_by varchar(120),
    ADD COLUMN IF NOT EXISTS lock_expires_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_email_notifications_status_lock_expires
    ON email_notifications (status, lock_expires_at);
