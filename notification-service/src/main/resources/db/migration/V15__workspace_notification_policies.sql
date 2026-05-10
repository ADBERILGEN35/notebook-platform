CREATE TABLE workspace_notification_policies (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  notification_type VARCHAR(100) NOT NULL,
  channel VARCHAR(50) NOT NULL,
  policy_mode VARCHAR(50) NOT NULL,
  reason TEXT,
  created_by_user_id UUID NOT NULL,
  updated_by_user_id UUID,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_workspace_notification_policy UNIQUE (workspace_id, notification_type, channel)
);

CREATE INDEX idx_workspace_notification_policies_workspace ON workspace_notification_policies (workspace_id);
CREATE INDEX idx_workspace_notification_policies_type_channel ON workspace_notification_policies (notification_type, channel);
