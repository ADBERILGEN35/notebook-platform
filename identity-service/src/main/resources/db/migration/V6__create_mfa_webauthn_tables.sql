CREATE TABLE user_webauthn_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    credential_id TEXT NOT NULL UNIQUE,
    public_key_cose TEXT NOT NULL,
    sign_count BIGINT,
    transports JSONB,
    attestation_type VARCHAR(100),
    aaguid VARCHAR(100),
    name VARCHAR(200),
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_user_webauthn_credentials_user_id
    ON user_webauthn_credentials(user_id);

CREATE TABLE user_mfa_settings (
    user_id UUID PRIMARY KEY,
    webauthn_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    backup_codes_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_mfa_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_mfa_recovery_codes_user_id
    ON user_mfa_recovery_codes(user_id);
