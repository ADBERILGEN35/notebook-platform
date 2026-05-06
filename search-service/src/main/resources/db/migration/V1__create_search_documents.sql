CREATE TABLE IF NOT EXISTS search_documents (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    notebook_id UUID,
    note_id UUID NOT NULL,
    title varchar(255) NOT NULL,
    content_text text NOT NULL,
    tags_text text,
    notebook_name text,
    created_by UUID,
    updated_by UUID,
    note_created_at timestamptz,
    note_updated_at timestamptz,
    archived_at timestamptz,
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(tags_text, '') || ' ' || coalesce(notebook_name, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(content_text, '')), 'C')
    ) STORED,
    indexed_at timestamptz NOT NULL,
    source_version int,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_search_documents_note_id ON search_documents (note_id);
CREATE INDEX IF NOT EXISTS idx_search_documents_workspace_id ON search_documents (workspace_id);
CREATE INDEX IF NOT EXISTS idx_search_documents_notebook_id ON search_documents (notebook_id);
CREATE INDEX IF NOT EXISTS idx_search_documents_archived_at ON search_documents (archived_at);
CREATE INDEX IF NOT EXISTS idx_search_documents_note_updated_at ON search_documents (note_updated_at);
CREATE INDEX IF NOT EXISTS idx_search_documents_search_vector ON search_documents USING GIN (search_vector);
