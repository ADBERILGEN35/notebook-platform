-- Run as a privileged inspection user or migration owner.
-- Override runtime role names with:
--   psql -v workspace_runtime_role=... -v content_runtime_role=... -f ...
\if :{?workspace_runtime_role}
\else
\set workspace_runtime_role 'notebook_workspace_runtime'
\endif
\if :{?content_runtime_role}
\else
\set content_runtime_role 'notebook_content_runtime'
\endif

WITH expected_roles(role_name) AS (
  VALUES (:'workspace_runtime_role'), (:'content_runtime_role')
)
SELECT
  r.role_name,
  pg_roles.rolsuper AS is_superuser,
  pg_roles.rolbypassrls AS bypasses_rls,
  pg_roles.rolcreatedb AS can_create_db,
  pg_roles.rolcreaterole AS can_create_role
FROM expected_roles r
JOIN pg_roles ON pg_roles.rolname = r.role_name
ORDER BY r.role_name;

WITH expected_tables(table_name) AS (
  VALUES
    ('workspace_members'),
    ('notebooks'),
    ('notebook_members'),
    ('tags'),
    ('notebook_tags'),
    ('invitations'),
    ('notes'),
    ('note_versions'),
    ('note_links'),
    ('comments'),
    ('note_tags')
),
expected_roles(role_name) AS (
  VALUES (:'workspace_runtime_role'), (:'content_runtime_role')
)
SELECT
  t.table_name,
  c.relowner::regrole::text AS table_owner,
  r.role_name,
  c.relowner::regrole::text = r.role_name AS runtime_is_owner,
  has_table_privilege(r.role_name, c.oid, 'SELECT') AS can_select,
  has_table_privilege(r.role_name, c.oid, 'INSERT') AS can_insert,
  has_table_privilege(r.role_name, c.oid, 'UPDATE') AS can_update,
  has_table_privilege(r.role_name, c.oid, 'DELETE') AS can_delete
FROM expected_tables t
JOIN pg_class c ON c.relname = t.table_name
JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
CROSS JOIN expected_roles r
WHERE c.relkind = 'r'
ORDER BY t.table_name, r.role_name;

SELECT
  'ddl_probe' AS check_name,
  'Runtime roles should not be table owners, superusers, BYPASSRLS, CREATEDB or CREATEROLE. DDL should be denied by grants/default privileges.' AS expected_result;
