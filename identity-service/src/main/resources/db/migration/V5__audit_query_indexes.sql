CREATE INDEX IF NOT EXISTS idx_identity_audit_events_workspace_id ON identity_audit_events (workspace_id);
CREATE INDEX IF NOT EXISTS idx_identity_audit_events_request_id ON identity_audit_events (request_id);
