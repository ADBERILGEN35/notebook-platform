-- Opt-in only. Validate with integration tests and a non-owner runtime role before production use.
ALTER TABLE workspace_members FORCE ROW LEVEL SECURITY;
ALTER TABLE notebooks FORCE ROW LEVEL SECURITY;
ALTER TABLE notebook_members FORCE ROW LEVEL SECURITY;
ALTER TABLE tags FORCE ROW LEVEL SECURITY;
ALTER TABLE notebook_tags FORCE ROW LEVEL SECURITY;
ALTER TABLE invitations FORCE ROW LEVEL SECURITY;

-- The workspaces table is intentionally excluded. It does not carry workspace_id and serves
-- user-level create/list flows that still rely on application-level authorization.
