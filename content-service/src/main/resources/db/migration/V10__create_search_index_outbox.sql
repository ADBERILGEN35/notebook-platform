CREATE TABLE IF NOT EXISTS search_index_outbox (
    id UUID PRIMARY KEY,
    event_type varchar(40) NOT NULL,
    status varchar(40) NOT NULL,
    workspace_id UUID NOT NULL,
    notebook_id UUID,
    note_id UUID NOT NULL,
    source_version int,
    payload jsonb NOT NULL,
    idempotency_key varchar(300) NOT NULL,
    attempt_count int NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    last_error text,
    locked_at timestamptz,
    processed_at timestamptz,
    failed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_search_index_outbox_idempotency_key
    ON search_index_outbox (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_search_index_outbox_status_next_attempt
    ON search_index_outbox (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_search_index_outbox_note_id
    ON search_index_outbox (note_id);
CREATE INDEX IF NOT EXISTS idx_search_index_outbox_workspace_id
    ON search_index_outbox (workspace_id);
CREATE INDEX IF NOT EXISTS idx_search_index_outbox_created_at
    ON search_index_outbox (created_at);
