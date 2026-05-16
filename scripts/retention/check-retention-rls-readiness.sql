-- Retention RLS readiness preflight (Faz 101).
--
-- Run as a privileged inspection user (migration owner) against the content-service
-- database. Override the retention role name with:
--   psql -v retention_role=notebook_content_retention -f ...
--
-- This script is read-only: it only runs SELECT and SHOW statements. No DDL,
-- no DML, no destructive operations. It is safe to run in any environment.
--
-- The script reports:
--   1. Existence and capability of the dedicated retention DB role.
--   2. Current session `row_security` setting.
--   3. Table-level SELECT privileges on retention count target tables.
--   4. Existence of the retention count supporting indexes.
--   5. RLS / FORCE RLS status on the target tables.
--   6. A bounded probe count to confirm the count query path runs.
--
-- The runbook (docs/retention-rls-production-runbook.md) interprets the output.

\if :{?retention_role}
\else
\set retention_role 'notebook_content_retention'
\endif

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

\echo == 3. table-level SELECT privileges ==
WITH expected_tables(table_name) AS (
  VALUES ('note_versions'), ('comments'), ('search_index_outbox')
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
  'Retention role needs only SELECT on note_versions, comments and search_index_outbox. INSERT/UPDATE/DELETE must be false.' AS expected_result;

\echo == 4. retention count indexes ==
SELECT
  indexname,
  tablename
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN ('idx_note_versions_created_at', 'idx_comments_created_at')
ORDER BY indexname;

SELECT
  'count_indexes' AS check_name,
  'Both idx_note_versions_created_at and idx_comments_created_at must exist (created by V14 migration).' AS expected_result;

\echo == 5. RLS status on retention target tables ==
WITH expected_tables(table_name) AS (
  VALUES ('note_versions'), ('comments'), ('search_index_outbox')
)
SELECT
  t.table_name,
  c.relrowsecurity   AS rls_enabled,
  c.relforcerowsecurity AS force_rls
FROM expected_tables t
JOIN pg_class c ON c.relname = t.table_name
JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
ORDER BY t.table_name;

SELECT
  'rls_status' AS check_name,
  'If RLS is enabled the retention role must either have rolbypassrls=true or connect with row_security=off; otherwise count queries silently return 0.' AS expected_result;

\echo == 6. bounded probe count ==
-- Probes match ContentRetentionCountRepository.cappedCount shape: bounded LIMIT
-- pattern, count only, no body/title/email selected.
SELECT 'probe_note_versions' AS probe, count(*) AS rows_seen
FROM (SELECT 1 FROM note_versions LIMIT 1) AS bounded;

SELECT 'probe_comments' AS probe, count(*) AS rows_seen
FROM (SELECT 1 FROM comments LIMIT 1) AS bounded;

SELECT 'probe_search_index_outbox' AS probe, count(*) AS rows_seen
FROM (SELECT 1 FROM search_index_outbox LIMIT 1) AS bounded;

SELECT
  'probe_result' AS check_name,
  'A non-zero rows_seen on at least one probe confirms the retention role can read the table; a permission_denied error indicates GRANT SELECT is missing.' AS expected_result;
