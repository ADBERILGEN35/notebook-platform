CREATE TABLE IF NOT EXISTS search_reindex_jobs (
    id UUID PRIMARY KEY,
    workspace_id UUID,
    notebook_id UUID,
    status varchar(40) NOT NULL,
    requested_by_service varchar(120),
    mode varchar(40) NOT NULL,
    total_scanned bigint NOT NULL DEFAULT 0,
    total_indexed bigint NOT NULL DEFAULT 0,
    total_failed bigint NOT NULL DEFAULT 0,
    last_cursor varchar(500),
    started_at timestamptz,
    completed_at timestamptz,
    failed_at timestamptz,
    last_error text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_search_reindex_jobs_status
    ON search_reindex_jobs (status);
CREATE INDEX IF NOT EXISTS idx_search_reindex_jobs_workspace_id
    ON search_reindex_jobs (workspace_id);
CREATE INDEX IF NOT EXISTS idx_search_reindex_jobs_created_at
    ON search_reindex_jobs (created_at);
