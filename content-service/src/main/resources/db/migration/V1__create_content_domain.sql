CREATE TABLE IF NOT EXISTS notes (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    notebook_id UUID NOT NULL,
    parent_note_id UUID REFERENCES notes(id),
    title varchar(255) NOT NULL,
    content_blocks jsonb NOT NULL,
    content_schema_version int NOT NULL DEFAULT 1,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    archived_at timestamptz,
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(content_blocks::text, '')), 'B')
    ) STORED
);

CREATE INDEX IF NOT EXISTS idx_notes_workspace_id ON notes (workspace_id);
CREATE INDEX IF NOT EXISTS idx_notes_notebook_id ON notes (notebook_id);
CREATE INDEX IF NOT EXISTS idx_notes_parent_note_id ON notes (parent_note_id);
CREATE INDEX IF NOT EXISTS idx_notes_created_by ON notes (created_by);
CREATE INDEX IF NOT EXISTS idx_notes_archived_at ON notes (archived_at);
CREATE INDEX IF NOT EXISTS idx_notes_search_vector ON notes USING GIN (search_vector);

CREATE TABLE IF NOT EXISTS note_versions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    note_id UUID NOT NULL REFERENCES notes(id),
    version_number int NOT NULL,
    title varchar(255) NOT NULL,
    content_blocks jsonb NOT NULL,
    content_schema_version int NOT NULL,
    created_by UUID NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_note_versions_note_version ON note_versions (note_id, version_number);
CREATE INDEX IF NOT EXISTS idx_note_versions_note_id ON note_versions (note_id);

CREATE TABLE IF NOT EXISTS note_links (
    from_note_id UUID NOT NULL REFERENCES notes(id),
    to_note_id UUID NOT NULL REFERENCES notes(id),
    workspace_id UUID NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (from_note_id, to_note_id)
);

CREATE INDEX IF NOT EXISTS idx_note_links_from_note_id ON note_links (from_note_id);
CREATE INDEX IF NOT EXISTS idx_note_links_to_note_id ON note_links (to_note_id);

CREATE TABLE IF NOT EXISTS comments (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    note_id UUID NOT NULL REFERENCES notes(id),
    user_id UUID NOT NULL,
    parent_comment_id UUID REFERENCES comments(id),
    block_id varchar(120),
    content text NOT NULL,
    resolved_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_comments_note_id ON comments (note_id);
CREATE INDEX IF NOT EXISTS idx_comments_user_id ON comments (user_id);
CREATE INDEX IF NOT EXISTS idx_comments_parent_comment_id ON comments (parent_comment_id);

CREATE TABLE IF NOT EXISTS note_tags (
    note_id UUID NOT NULL REFERENCES notes(id),
    tag_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (note_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_note_tags_tag_id ON note_tags (tag_id);
CREATE INDEX IF NOT EXISTS idx_note_tags_workspace_id ON note_tags (workspace_id);

ALTER TABLE notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE note_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE note_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE note_tags ENABLE ROW LEVEL SECURITY;

CREATE POLICY notes_workspace_isolation ON notes
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY note_versions_workspace_isolation ON note_versions
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY note_links_workspace_isolation ON note_links
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY comments_workspace_isolation ON comments
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY note_tags_workspace_isolation ON note_tags
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));

