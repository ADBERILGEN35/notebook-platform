ALTER TABLE search_documents
    ADD COLUMN IF NOT EXISTS last_seen_reindex_job_id UUID,
    ADD COLUMN IF NOT EXISTS last_seen_reindex_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_search_documents_last_seen_reindex_job_id
    ON search_documents (last_seen_reindex_job_id);
CREATE INDEX IF NOT EXISTS idx_search_documents_workspace_last_seen_reindex
    ON search_documents (workspace_id, last_seen_reindex_job_id);
CREATE INDEX IF NOT EXISTS idx_search_documents_workspace_notebook_last_seen_reindex
    ON search_documents (workspace_id, notebook_id, last_seen_reindex_job_id);

ALTER TABLE search_reindex_jobs
    ADD COLUMN IF NOT EXISTS total_archived_orphans bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cleanup_started_at timestamptz,
    ADD COLUMN IF NOT EXISTS cleanup_completed_at timestamptz,
    ADD COLUMN IF NOT EXISTS cleanup_orphans_requested boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS cleanup_orphans_executed boolean NOT NULL DEFAULT false;
