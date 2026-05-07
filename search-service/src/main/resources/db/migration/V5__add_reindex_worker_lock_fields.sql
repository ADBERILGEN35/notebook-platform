ALTER TABLE search_reindex_jobs
    ADD COLUMN IF NOT EXISTS locked_by varchar(120),
    ADD COLUMN IF NOT EXISTS lock_expires_at timestamptz,
    ADD COLUMN IF NOT EXISTS heartbeat_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_search_reindex_jobs_status_lock_expires
    ON search_reindex_jobs (status, lock_expires_at);
