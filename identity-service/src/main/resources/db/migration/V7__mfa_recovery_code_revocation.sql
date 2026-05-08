ALTER TABLE user_mfa_recovery_codes
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;
