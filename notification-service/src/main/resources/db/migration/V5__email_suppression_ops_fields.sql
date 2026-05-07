ALTER TABLE email_suppressions
    ADD COLUMN IF NOT EXISTS released_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_email_suppressions_active
    ON email_suppressions (reason, expires_at, released_at);
