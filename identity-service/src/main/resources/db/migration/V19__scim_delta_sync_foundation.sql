ALTER TABLE users
  ADD COLUMN IF NOT EXISTS deprovision_reason VARCHAR(120),
  ADD COLUMN IF NOT EXISTS last_scim_external_id VARCHAR(255),
  ADD COLUMN IF NOT EXISTS reactivated_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS scim_sync_checkpoints (
  id UUID PRIMARY KEY,
  provider VARCHAR(80) NOT NULL,
  resource_type VARCHAR(20) NOT NULL,
  sync_mode VARCHAR(20) NOT NULL,
  checkpoint_token VARCHAR(1024),
  last_successful_sync_at TIMESTAMPTZ,
  last_attempt_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ux_scim_sync_checkpoint UNIQUE (provider, resource_type)
);

CREATE INDEX IF NOT EXISTS ix_scim_sync_checkpoints_status
  ON scim_sync_checkpoints(status);

CREATE TABLE IF NOT EXISTS scim_sync_runs (
  id UUID PRIMARY KEY,
  provider VARCHAR(80) NOT NULL,
  resource_type VARCHAR(20) NOT NULL,
  sync_mode VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  processed_count BIGINT NOT NULL DEFAULT 0,
  created_count BIGINT NOT NULL DEFAULT 0,
  updated_count BIGINT NOT NULL DEFAULT 0,
  deprovisioned_count BIGINT NOT NULL DEFAULT 0,
  skipped_count BIGINT NOT NULL DEFAULT 0,
  error_count BIGINT NOT NULL DEFAULT 0,
  last_error_code VARCHAR(120),
  last_error_summary VARCHAR(512),
  request_id VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS ix_scim_sync_runs_provider_resource_started
  ON scim_sync_runs(provider, resource_type, started_at DESC);

CREATE INDEX IF NOT EXISTS ix_scim_sync_runs_status_started
  ON scim_sync_runs(status, started_at DESC);
