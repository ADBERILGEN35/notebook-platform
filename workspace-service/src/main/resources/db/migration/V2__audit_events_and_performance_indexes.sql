CREATE TABLE IF NOT EXISTS workspace_audit_events (
    id UUID PRIMARY KEY,
    event_type varchar(120) NOT NULL,
    actor_user_id UUID,
    workspace_id UUID,
    aggregate_type varchar(120) NOT NULL,
    aggregate_id UUID,
    request_id varchar(120),
    ip_address varchar(120),
    user_agent varchar(512),
    metadata jsonb,
    created_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_workspace_audit_events_event_type ON workspace_audit_events (event_type);
CREATE INDEX IF NOT EXISTS idx_workspace_audit_events_actor_user_id ON workspace_audit_events (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_workspace_audit_events_workspace_id ON workspace_audit_events (workspace_id);
CREATE INDEX IF NOT EXISTS idx_workspace_audit_events_aggregate ON workspace_audit_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_workspace_audit_events_created_at ON workspace_audit_events (created_at);

CREATE INDEX IF NOT EXISTS idx_notebooks_workspace_active ON notebooks (workspace_id, archived_at);
CREATE INDEX IF NOT EXISTS idx_tags_workspace_scope_active ON tags (workspace_id, scope, archived_at);
CREATE INDEX IF NOT EXISTS idx_invitations_workspace_pending ON invitations (workspace_id, accepted_at, revoked_at);
