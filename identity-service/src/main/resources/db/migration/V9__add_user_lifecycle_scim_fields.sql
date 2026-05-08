ALTER TABLE users
  ADD COLUMN IF NOT EXISTS source VARCHAR(40) NOT NULL DEFAULT 'LOCAL',
  ADD COLUMN IF NOT EXISTS scim_external_id VARCHAR(255),
  ADD COLUMN IF NOT EXISTS deprovisioned_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_scim_external_id
  ON users(scim_external_id)
  WHERE scim_external_id IS NOT NULL;
