CREATE TABLE IF NOT EXISTS platform_admin_change_requests (
  id UUID PRIMARY KEY,
  requested_by_user_id UUID NOT NULL REFERENCES users(id),
  requested_by_email VARCHAR(320),
  operation_type VARCHAR(80) NOT NULL,
  target_service VARCHAR(80) NOT NULL,
  target_key VARCHAR(160) NOT NULL,
  current_value VARCHAR(500),
  requested_value VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL,
  impact_summary JSONB,
  validation_result JSONB,
  external_request_id VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  decided_at TIMESTAMPTZ,
  decided_by_user_id UUID,
  applied_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_platform_admin_change_requests_status
  ON platform_admin_change_requests(status);
CREATE INDEX IF NOT EXISTS ix_platform_admin_change_requests_requested_by
  ON platform_admin_change_requests(requested_by_user_id);
CREATE INDEX IF NOT EXISTS ix_platform_admin_change_requests_created_at
  ON platform_admin_change_requests(created_at DESC);
