-- Content-service PostgreSQL role setup template.
-- Run as a DBA/superuser or database owner before deploying a non-owner runtime user.
-- Replace passwords through your secret manager; do not commit real values.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notebook_content_migrator') THEN
    CREATE ROLE notebook_content_migrator LOGIN PASSWORD '<set-from-secret-manager>';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notebook_content_runtime') THEN
    CREATE ROLE notebook_content_runtime LOGIN PASSWORD '<set-from-secret-manager>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
  END IF;
END
$$;

GRANT CONNECT ON DATABASE notebook_platform TO notebook_content_migrator;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_content_runtime;
GRANT USAGE ON SCHEMA public TO notebook_content_migrator;
GRANT USAGE ON SCHEMA public TO notebook_content_runtime;

GRANT SELECT, INSERT, UPDATE, DELETE ON
  notes,
  note_versions,
  note_links,
  comments,
  note_tags,
  content_audit_events
TO notebook_content_runtime;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO notebook_content_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE notebook_content_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO notebook_content_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE notebook_content_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO notebook_content_runtime;
