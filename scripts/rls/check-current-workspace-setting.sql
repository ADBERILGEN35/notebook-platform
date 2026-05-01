-- Run with the same runtime database user used by workspace-service or content-service.
SELECT current_setting('app.current_workspace_id', true) AS before_transaction;

BEGIN;
SELECT set_config('app.current_workspace_id', '00000000-0000-0000-0000-000000000001', true);
SELECT current_setting('app.current_workspace_id', true) AS inside_transaction;
COMMIT;

SELECT current_setting('app.current_workspace_id', true) AS after_transaction;

-- Expected:
-- before_transaction: empty/null
-- inside_transaction: 00000000-0000-0000-0000-000000000001
-- after_transaction: empty/null, proving SET LOCAL semantics do not leak across transactions.
