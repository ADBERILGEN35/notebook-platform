CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY,
    slug varchar(120) NOT NULL,
    name varchar(180) NOT NULL,
    type varchar(40) NOT NULL,
    owner_id UUID NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    archived_at timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_workspaces_slug ON workspaces (slug);
CREATE UNIQUE INDEX IF NOT EXISTS ux_workspaces_personal_owner_active
    ON workspaces (owner_id)
    WHERE type = 'PERSONAL' AND archived_at IS NULL;

CREATE TABLE IF NOT EXISTS workspace_members (
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL,
    role varchar(40) NOT NULL,
    joined_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_workspace_members_user_id ON workspace_members (user_id);
CREATE INDEX IF NOT EXISTS idx_workspace_members_workspace_id ON workspace_members (workspace_id);
CREATE INDEX IF NOT EXISTS idx_workspace_members_workspace_role ON workspace_members (workspace_id, role);

CREATE TABLE IF NOT EXISTS notebooks (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name varchar(180) NOT NULL,
    icon varchar(80),
    created_by UUID NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    archived_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_notebooks_workspace_id ON notebooks (workspace_id);

CREATE TABLE IF NOT EXISTS notebook_members (
    notebook_id UUID NOT NULL REFERENCES notebooks(id),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL,
    role varchar(40) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (notebook_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_notebook_members_user_id ON notebook_members (user_id);
CREATE INDEX IF NOT EXISTS idx_notebook_members_workspace_id ON notebook_members (workspace_id);

CREATE TABLE IF NOT EXISTS tags (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name varchar(120) NOT NULL,
    color varchar(16),
    scope varchar(40) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    archived_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_tags_workspace_id ON tags (workspace_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_tags_workspace_name_scope_active
    ON tags (workspace_id, lower(name), scope)
    WHERE archived_at IS NULL;

CREATE TABLE IF NOT EXISTS notebook_tags (
    notebook_id UUID NOT NULL REFERENCES notebooks(id),
    tag_id UUID NOT NULL REFERENCES tags(id),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (notebook_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_notebook_tags_workspace_id ON notebook_tags (workspace_id);

CREATE TABLE IF NOT EXISTS invitations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    email varchar(320) NOT NULL,
    token_hash varchar(128) NOT NULL,
    role varchar(40) NOT NULL,
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    revoked_at timestamptz,
    created_by UUID NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_invitations_token_hash ON invitations (token_hash);
CREATE INDEX IF NOT EXISTS idx_invitations_email ON invitations (lower(email));
CREATE INDEX IF NOT EXISTS idx_invitations_workspace_id ON invitations (workspace_id);
CREATE INDEX IF NOT EXISTS idx_invitations_expires_at ON invitations (expires_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_invitations_workspace_email_pending
    ON invitations (workspace_id, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

ALTER TABLE workspace_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE notebooks ENABLE ROW LEVEL SECURITY;
ALTER TABLE notebook_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE notebook_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;

CREATE POLICY workspace_members_workspace_isolation ON workspace_members
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY notebooks_workspace_isolation ON notebooks
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY notebook_members_workspace_isolation ON notebook_members
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY tags_workspace_isolation ON tags
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY notebook_tags_workspace_isolation ON notebook_tags
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY invitations_workspace_isolation ON invitations
    USING (workspace_id::text = current_setting('app.current_workspace_id', true));

