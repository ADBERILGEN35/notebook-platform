ALTER TABLE notebooks
    ADD COLUMN IF NOT EXISTS permission_version integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_notebooks_permission_version
    ON notebooks (permission_version);

