-- Run as migration owner or read-only inspection role.
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
)
SELECT
  n.nspname AS schema_name,
  c.relname AS table_name,
  c.relrowsecurity AS row_security_enabled,
  c.relforcerowsecurity AS force_row_security_enabled
FROM expected_tables t
JOIN pg_class c ON c.relname = t.table_name
JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
WHERE c.relkind = 'r'
ORDER BY c.relname;

SELECT
  schemaname,
  tablename,
  policyname,
  permissive,
  roles,
  cmd,
  qual,
  with_check
FROM pg_policies
WHERE schemaname = 'public'
  AND tablename IN (
    'workspace_members',
    'notebooks',
    'notebook_members',
    'tags',
    'notebook_tags',
    'invitations',
    'notes',
    'note_versions',
    'note_links',
    'comments',
    'note_tags'
  )
ORDER BY tablename, policyname;
