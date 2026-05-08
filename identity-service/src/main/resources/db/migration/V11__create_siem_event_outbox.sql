CREATE TABLE IF NOT EXISTS siem_event_outbox (
  id UUID PRIMARY KEY,
  event_type VARCHAR(120) NOT NULL,
  category VARCHAR(80) NOT NULL,
  severity VARCHAR(40) NOT NULL,
  source_service VARCHAR(80) NOT NULL,
  subject_user_id UUID,
  actor_user_id UUID,
  workspace_id UUID,
  request_id VARCHAR(120),
  payload JSONB NOT NULL,
  status VARCHAR(40) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL,
  locked_at TIMESTAMPTZ,
  locked_by VARCHAR(120),
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  sent_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_siem_event_outbox_status_next_attempt
  ON siem_event_outbox(status, next_attempt_at);

CREATE INDEX IF NOT EXISTS ix_siem_event_outbox_category_created_at
  ON siem_event_outbox(category, created_at);

CREATE INDEX IF NOT EXISTS ix_siem_event_outbox_request_id
  ON siem_event_outbox(request_id);
