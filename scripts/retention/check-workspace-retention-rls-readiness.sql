-- Workspace retention RLS readiness preflight (Faz 107).
--
-- Run as a privileged inspection user (migration owner) against the
-- workspace-service database. Override the retention role name with:
--   psql -v retention_role=notebook_workspace_retention -f ...
--
-- Read-only: SELECT and SHOW only. No DDL, DML, or destructive operations.
--
-- Reports:
--   1. Current session user and retention role capability.
--   2. row_security session setting.
--   3. Retention target table/column presence (invitations, workspace_audit_events).
--   4. Table-level SELECT privileges.
--   5. Retention count indexes.
--   6. RLS status on retention-related tables.
--   7. Bounded aggregate count probes (matches WorkspaceRetentionCountRepository).
--
-- Interpretation: docs/workspace-retention-rls-production-runbook.md

\if :{?retention_role}
\else
\set retention_role 'notebook_workspace_retention'
\endif

\echo == 0. current database user ==
SELECT current_user AS session_user, session_user AS effective_user;

\echo == 1. retention role capability ==
SELECT
  rolname,
  rolsuper        AS is_superuser,
  rolbypassrls    AS bypasses_rls,
  rolcanlogin     AS can_login,
  rolcreatedb     AS can_create_db,
  rolcreaterole   AS can_create_role
FROM pg_roles
WHERE rolname = :'retention_role';

SELECT
  'role_capability' AS check_name,
  'Retention role should exist, be allowed to log in, have rolbypassrls=true and rolsuper=false. CREATEDB and CREATEROLE should be false.' AS expected_result;

\echo == 2. row_security session setting ==
SHOW row_security;

SELECT
  'row_security_setting' AS check_name,
  'For BYPASSRLS roles the session value is informational; for non-BYPASSRLS retention connections set row_security=off explicitly.' AS expected_result;

\echo == 3. retention target columns ==
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (
    (table_name = 'invitations' AND column_name IN ('expires_at', 'accepted_at', 'revoked_at', 'created_at'))
    OR (table_name = 'workspace_audit_events' AND column_name IN ('created_at', 'event_type', 'workspace_id'))
  )
ORDER BY table_name, column_name;

SELECT
  'target_columns' AS check_name,
  'invitations must expose expires_at, accepted_at, revoked_at; workspace_audit_events must expose created_at for audit-like retention counts.' AS expected_result;

\echo == 4. table-level SELECT privileges ==
WITH expected_tables(table_name) AS (
  VALUES ('invitations'), ('workspace_audit_events')
)
SELECT
  t.table_name,
  has_table_privilege(:'retention_role', 'public.' || t.table_name, 'SELECT') AS can_select,
  has_table_privilege(:'retention_role', 'public.' || t.table_name, 'INSERT') AS can_insert,
  has_table_privilege(:'retention_role', 'public.' || t.table_name, 'UPDATE') AS can_update,
  has_table_privilege(:'retention_role', 'public.' || t.table_name, 'DELETE') AS can_delete
FROM expected_tables t
ORDER BY t.table_name;

SELECT
  'table_privileges' AS check_name,
  'Retention role needs only SELECT on invitations and workspace_audit_events. INSERT/UPDATE/DELETE must be false.' AS expected_result;

\echo == 5. retention count indexes ==
SELECT indexname, tablename
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
    'idx_invitations_expires_at',
    'idx_invitations_workspace_pending',
    'idx_workspace_audit_events_created_at'
  )
ORDER BY indexname;

SELECT
  'count_indexes' AS check_name,
  'idx_invitations_expires_at and idx_workspace_audit_events_created_at should exist; pending invitation index is informational.' AS expected_result;

\echo == 6. RLS status on retention target tables ==
WITH expected_tables(table_name) AS (
  VALUES ('invitations'), ('workspace_audit_events')
)
SELECT
  t.table_name,
  c.relrowsecurity      AS rls_enabled,
  c.relforcerowsecurity AS force_rls
FROM expected_tables t
JOIN pg_class c ON c.relname = t.table_name
JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
ORDER BY t.table_name;

SELECT
  'rls_status' AS check_name,
  'invitations has RLS in baseline schema; workspace_audit_events typically has no RLS. If RLS is enabled on invitations, retention role needs BYPASSRLS or row_security=off or counts return 0.' AS expected_result;

\echo == 7. bounded probe count ==
SELECT 'probe_expired_invitations' AS probe, count(*) AS rows_seen
FROM (
  SELECT 1 FROM invitations
  WHERE expires_at < now() - interval '90 days'
    AND accepted_at IS NULL
    AND revoked_at IS NULL
  LIMIT 1
) AS bounded;

SELECT 'probe_workspace_audit_events' AS probe, count(*) AS rows_seen
FROM (
  SELECT 1 FROM workspace_audit_events
  WHERE created_at < now() - interval '365 days'
  LIMIT 1
) AS bounded;

SELECT
  'probe_result' AS check_name,
  'Probes use bounded LIMIT only; no email, workspace name, or token columns selected. permission_denied indicates missing GRANT SELECT.' AS expected_result;
