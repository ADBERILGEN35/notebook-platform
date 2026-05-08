CREATE TABLE IF NOT EXISTS external_identities (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  provider VARCHAR(100) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  email VARCHAR(320) NOT NULL,
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  claims JSONB,
  linked_at TIMESTAMPTZ NOT NULL,
  last_login_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_external_identities_provider_subject
  ON external_identities(provider, subject);

CREATE INDEX IF NOT EXISTS ix_external_identities_user_id
  ON external_identities(user_id);

CREATE INDEX IF NOT EXISTS ix_external_identities_provider_email
  ON external_identities(provider, email);
