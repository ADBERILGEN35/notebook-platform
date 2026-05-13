CREATE TABLE IF NOT EXISTS platform_legal_holds (
  id UUID PRIMARY KEY,
  hold_key VARCHAR(200) NOT NULL UNIQUE,
  scope VARCHAR(40) NOT NULL,
  scope_ref_id UUID,
  reason TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_by_user_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  released_by_user_id UUID,
  released_at TIMESTAMPTZ,
  release_reason TEXT,
  expires_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_platform_legal_holds_status_created
  ON platform_legal_holds(status, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_platform_legal_holds_scope_status
  ON platform_legal_holds(scope, status);
