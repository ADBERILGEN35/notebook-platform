ALTER TABLE search_reindex_jobs
    ADD COLUMN IF NOT EXISTS dry_run_cleanup boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS cleanup_preview_count bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cleanup_preview_generated_at timestamptz;
