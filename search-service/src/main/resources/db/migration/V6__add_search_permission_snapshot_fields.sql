ALTER TABLE search_documents
    ADD COLUMN IF NOT EXISTS permission_version integer,
    ADD COLUMN IF NOT EXISTS visibility_mode varchar(32),
    ADD COLUMN IF NOT EXISTS workspace_readable boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS restricted boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS permission_indexed_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_search_documents_restricted ON search_documents (restricted);
CREATE INDEX IF NOT EXISTS idx_search_documents_workspace_readable ON search_documents (workspace_readable);

