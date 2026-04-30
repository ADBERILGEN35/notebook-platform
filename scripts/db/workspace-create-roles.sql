-- Workspace-service PostgreSQL role setup template.
-- Run as a DBA/superuser or database owner before deploying a non-owner runtime user.
-- Replace passwords through your secret manager; do not commit real values.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notebook_workspace_migrator') THEN
    CREATE ROLE notebook_workspace_migrator LOGIN PASSWORD '<set-from-secret-manager>';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notebook_workspace_runtime') THEN
    CREATE ROLE notebook_workspace_runtime LOGIN PASSWORD '<set-from-secret-manager>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
  END IF;
END
$$;

GRANT CONNECT ON DATABASE notebook_platform TO notebook_workspace_migrator;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_workspace_runtime;
GRANT USAGE ON SCHEMA public TO notebook_workspace_migrator;
GRANT USAGE ON SCHEMA public TO notebook_workspace_runtime;

GRANT SELECT, INSERT, UPDATE, DELETE ON
  workspaces,
  workspace_members,
  notebooks,
  notebook_members,
  tags,
  notebook_tags,
  invitations,
  workspace_audit_events
TO notebook_workspace_runtime;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO notebook_workspace_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE notebook_workspace_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO notebook_workspace_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE notebook_workspace_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO notebook_workspace_runtime;
