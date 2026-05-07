ALTER TABLE search_index_outbox
    ADD COLUMN IF NOT EXISTS locked_by varchar(120),
    ADD COLUMN IF NOT EXISTS lock_expires_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_search_index_outbox_status_lock_expires
    ON search_index_outbox (status, lock_expires_at);
