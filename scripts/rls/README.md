# RLS Preflight Scripts

These SQL scripts support staged Runtime RLS rollout. They do not change application data and do not
enable FORCE RLS.

## Scripts

- `check-db-role-permissions.sql`: verifies runtime roles are not superusers, do not have
  `BYPASSRLS`, are not table owners, and have expected DML privileges.
- `check-rls-status.sql`: lists RLS and FORCE RLS status plus policies for tenant-scoped tables.
- `check-current-workspace-setting.sql`: verifies `app.current_workspace_id` behaves like a
  transaction-local setting.

## How To Run

Run status and role checks as a migration owner or privileged inspection role:

```bash
psql "$DB_MIGRATION_URL" \
  -v workspace_runtime_role=notebook_workspace_runtime \
  -v content_runtime_role=notebook_content_runtime \
  -f scripts/rls/check-db-role-permissions.sql

psql "$DB_MIGRATION_URL" -f scripts/rls/check-rls-status.sql
```

Run the session-setting check as the runtime user:

```bash
psql "$DB_RUNTIME_URL" -f scripts/rls/check-current-workspace-setting.sql
```

## Expected Results

- Runtime roles are not table owners.
- Runtime roles have `rolsuper=false` and `rolbypassrls=false`.
- Tenant-scoped tables have RLS enabled before Stage 4.
- FORCE RLS is `false` until the opt-in enable scripts are deliberately applied.
- `app.current_workspace_id` is empty before and after a transaction and populated only inside it.

If any check fails, stop the rollout before enabling `APP_RLS_ENABLED` or FORCE RLS.

## Wrapper Scripts

Faz 22 adds guarded wrappers:

- `run-preflight-checks.sh`
- `run-force-rls-enable.sh`
- `run-force-rls-disable.sh`

Example:

```bash
DB_URL=postgresql://host:5432/notebook_platform \
DB_USER=notebook_migrator \
DB_PASSWORD=<secret> \
CONFIRM_ENVIRONMENT=staging \
bash scripts/rls/run-force-rls-enable.sh
```

The FORCE wrappers refuse to run unless `CONFIRM_ENVIRONMENT=staging` or
`CONFIRM_FORCE_RLS=true` is set. Do not use the override for production without a separately
approved rollout or incident rollback.
