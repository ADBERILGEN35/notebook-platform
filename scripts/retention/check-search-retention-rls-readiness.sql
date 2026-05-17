-- Search retention RLS readiness preflight (Faz 107).
--
-- Run as a privileged inspection user (migration owner) against the
-- search-service database. Override the retention role name with:
--   psql -v retention_role=notebook_search_retention -f ...
--
-- Read-only: SELECT and SHOW only. No DDL, DML, or destructive operations.
--
-- Reports:
--   1. Current session user and retention role capability.
--   2. row_security session setting.
--   3. Retention target table/column presence (search_documents, search_reindex_jobs).
--   4. Table-level SELECT privileges.
--   5. Retention count indexes.
--   6. RLS status (search DB typically has no RLS; reported for completeness).
--   7. Bounded aggregate count probes (matches SearchRetentionCountRepository).
--
-- Interpretation: docs/search-retention-rls-production-runbook.md

\if :{?retention_role}
\else
\set retention_role 'notebook_search_retention'
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
  'Search-service DB typically has no tenant RLS; session row_security is informational unless platform RLS is added later.' AS expected_result;

\echo == 3. retention target columns ==
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (
    (table_name = 'search_documents'
      AND column_name IN ('archived_at', 'indexed_at', 'note_updated_at', 'updated_at'))
    OR (table_name = 'search_reindex_jobs'
      AND column_name IN (
        'status', 'completed_at', 'failed_at', 'updated_at', 'created_at'))
  )
ORDER BY table_name, column_name;

SELECT
  'target_columns' AS check_name,
  'search_documents.archived_at and search_reindex_jobs terminal timestamp columns must exist for dry-run counts.' AS expected_result;

\echo == 4. table-level SELECT privileges ==
WITH expected_tables(table_name) AS (
  VALUES ('search_documents'), ('search_reindex_jobs')
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
  'Retention role needs only SELECT on search_documents and search_reindex_jobs. INSERT/UPDATE/DELETE must be false.' AS expected_result;

\echo == 5. retention count indexes ==
SELECT indexname, tablename
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
    'idx_search_documents_archived_at',
    'idx_search_documents_note_updated_at',
    'idx_search_reindex_jobs_status',
    'idx_search_reindex_jobs_created_at'
  )
ORDER BY indexname;

SELECT
  'count_indexes' AS check_name,
  'Archived document and terminal job count paths should use indexed predicates; all four indexes should exist.' AS expected_result;

\echo == 6. RLS status on retention target tables ==
WITH expected_tables(table_name) AS (
  VALUES ('search_documents'), ('search_reindex_jobs')
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
  'Expected rls_enabled=false on search-service tables today. If RLS is enabled later, retention role needs BYPASSRLS or row_security=off.' AS expected_result;

\echo == 7. bounded probe count ==
SELECT 'probe_archived_documents' AS probe, count(*) AS rows_seen
FROM (
  SELECT 1 FROM search_documents
  WHERE archived_at IS NOT NULL
    AND archived_at < now() - interval '90 days'
  LIMIT 1
) AS bounded;

SELECT 'probe_terminal_reindex_jobs' AS probe, count(*) AS rows_seen
FROM (
  SELECT 1 FROM search_reindex_jobs
  WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')
    AND COALESCE(completed_at, failed_at, updated_at, created_at) < now() - interval '90 days'
  LIMIT 1
) AS bounded;

SELECT
  'probe_result' AS check_name,
  'Probes use bounded LIMIT only; no title, content_text, search_vector, snippet, or query text selected.' AS expected_result;
