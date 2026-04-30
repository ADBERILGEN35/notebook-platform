CREATE TABLE IF NOT EXISTS content_audit_events (
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

CREATE INDEX IF NOT EXISTS idx_content_audit_events_event_type ON content_audit_events (event_type);
CREATE INDEX IF NOT EXISTS idx_content_audit_events_actor_user_id ON content_audit_events (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_content_audit_events_workspace_id ON content_audit_events (workspace_id);
CREATE INDEX IF NOT EXISTS idx_content_audit_events_aggregate ON content_audit_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_content_audit_events_created_at ON content_audit_events (created_at);

CREATE INDEX IF NOT EXISTS idx_notes_workspace_notebook_active ON notes (workspace_id, notebook_id, archived_at);
CREATE INDEX IF NOT EXISTS idx_note_versions_workspace_note ON note_versions (workspace_id, note_id);
CREATE INDEX IF NOT EXISTS idx_note_links_workspace_from ON note_links (workspace_id, from_note_id);
CREATE INDEX IF NOT EXISTS idx_note_links_workspace_to ON note_links (workspace_id, to_note_id);
CREATE INDEX IF NOT EXISTS idx_comments_note_deleted ON comments (note_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_note_tags_workspace_note ON note_tags (workspace_id, note_id);
