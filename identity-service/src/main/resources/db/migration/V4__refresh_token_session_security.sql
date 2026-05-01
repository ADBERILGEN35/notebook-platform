ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS revoked_reason varchar(80),
    ADD COLUMN IF NOT EXISTS revoked_by_user_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS last_used_at timestamptz,
    ADD COLUMN IF NOT EXISTS replaced_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_active_user
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;
