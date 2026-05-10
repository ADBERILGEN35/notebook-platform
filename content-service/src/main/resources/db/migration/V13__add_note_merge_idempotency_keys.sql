CREATE TABLE IF NOT EXISTS note_merge_idempotency_keys (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    note_id UUID NOT NULL REFERENCES notes(id),
    idempotency_key varchar(128) NOT NULL,
    request_hash varchar(128) NOT NULL,
    status varchar(32) NOT NULL,
    result_etag varchar(128),
    result_version int,
    result_note_id UUID,
    created_at timestamptz NOT NULL,
    completed_at timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_note_merge_idempotency_key
    ON note_merge_idempotency_keys (user_id, note_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_note_merge_idempotency_created_at
    ON note_merge_idempotency_keys (created_at);
CREATE INDEX IF NOT EXISTS idx_note_merge_idempotency_note_id
    ON note_merge_idempotency_keys (note_id);

ALTER TABLE note_merge_idempotency_keys ENABLE ROW LEVEL SECURITY;

CREATE POLICY note_merge_idempotency_workspace_isolation ON note_merge_idempotency_keys
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
