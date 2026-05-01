# Runtime RLS Rollout

Faz 16 turns the Faz 11-12 tenant/RLS foundation into a staged production rollout plan. Application
authorization remains active in every stage. DB-level enforcement is counted as active only after the
stage has been validated with runtime credentials and the relevant RLS policies.

## Stage 0 - Current Safe Mode

Config:

- `APP_RLS_ENABLED=false`
- `APP_RLS_STRICT_WORKSPACE_HEADER=false`
- Existing application-level authorization active.

Purpose:

- Baseline production behavior.
- No DB session tenant setting is required.
- Aggregate-id endpoints keep backward-compatible header behavior.

Validation:

- Existing smoke test passes.
- Tenant-related error rates are stable.

## Stage 1 - Strict Header Readiness

Config:

- `APP_RLS_ENABLED=false`
- `APP_RLS_STRICT_WORKSPACE_HEADER=true`

Purpose:

- Verify gateway/clients send `X-Workspace-Id` for aggregate-id tenant endpoints.
- Keep DB-level RLS disabled while contract readiness is measured.

Expected errors:

- Missing header: `400 MISSING_WORKSPACE_CONTEXT`
- Conflicting header: `400 INVALID_WORKSPACE_CONTEXT`

Validation:

```bash
STRICT_MODE_EXPECTED=true bash scripts/smoke-test-rls-strict.sh
```

Rollback:

- Set `APP_RLS_STRICT_WORKSPACE_HEADER=false`.
- Restart workspace-service and content-service.

## Stage 2 - Runtime Tenant Context Enabled

Config:

- `APP_RLS_ENABLED=true`
- `APP_RLS_STRICT_WORKSPACE_HEADER=true`
- Existing DB roles may still be in use.
- FORCE RLS is not enabled.

Purpose:

- Verify tenant-aware service transactions consistently execute
  `SET LOCAL app.current_workspace_id`.
- Catch transaction/session setting issues before changing runtime DB credentials.

Preflight:

```bash
psql "$DB_RUNTIME_URL" -f scripts/rls/check-current-workspace-setting.sql
```

Validation:

- `scripts/smoke-test-rls-strict.sh` passes.
- No spike in DB errors involving `app.current_workspace_id`.

Rollback:

- Set `APP_RLS_ENABLED=false`.
- Restart workspace-service and content-service.

## Stage 3 - Non-owner Runtime DB Role

Config:

- `APP_RLS_ENABLED=true`
- `APP_RLS_STRICT_WORKSPACE_HEADER=true`
- workspace/content use `DB_RUNTIME_USER` and `DB_RUNTIME_PASSWORD`.
- Flyway uses `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD`.

Purpose:

- Ensure application runtime does not use table-owner credentials.
- Remove owner bypass as the default runtime path.

Preflight:

```bash
psql "$DB_MIGRATION_URL" \
  -v workspace_runtime_role=notebook_workspace_runtime \
  -v content_runtime_role=notebook_content_runtime \
  -f scripts/rls/check-db-role-permissions.sql

psql "$DB_MIGRATION_URL" -f scripts/rls/check-rls-status.sql
```

Validation:

- Runtime roles are not superusers, do not have `BYPASSRLS`, and are not table owners.
- Application pods start with runtime credentials.
- Flyway still runs with migration credentials.

Rollback:

- Temporarily restore the previous known-good datasource credential.
- Do not keep migration owner credentials as permanent runtime credentials.

## Stage 4 - FORCE RLS Opt-in

Config:

- Stage 3 remains active.
- Apply FORCE RLS opt-in scripts manually after staging validation:
  - `scripts/db/enable-force-rls-workspace.sql`
  - `scripts/db/enable-force-rls-content.sql`

Purpose:

- Prevent table-owner bypass for the selected tenant-scoped tables.
- Keep `workspaces` excluded because it does not carry `workspace_id` and serves user-level flows.

Preflight:

- Stage 3 has run cleanly in staging.
- `check-rls-status.sql` shows expected RLS policies.
- Rollback scripts are available and tested:
  - `scripts/db/disable-force-rls-workspace.sql`
  - `scripts/db/disable-force-rls-content.sql`

Validation:

- Staging smoke and regression checks pass.
- Production rollout is performed table-group by table-group if risk is high.

Rollback:

```bash
psql "$DB_MIGRATION_URL" -f scripts/db/disable-force-rls-workspace.sql
psql "$DB_MIGRATION_URL" -f scripts/db/disable-force-rls-content.sql
```

## Stage 5 - Production Steady State

Config:

- `APP_RLS_ENABLED=true`
- `APP_RLS_STRICT_WORKSPACE_HEADER=true`
- Non-owner runtime DB roles active.
- FORCE RLS enabled on validated table groups.

Purpose:

- Application-level authorization and DB-level tenant enforcement both active.
- Runtime credential model avoids owner bypass.

Ongoing checks:

- Run `scripts/rls/check-rls-status.sql` after schema changes.
- Re-run strict smoke tests after deployment and key DB permission changes.

## Header Contract

When strict mode is enabled, aggregate-id endpoints require `X-Workspace-Id` because the tenant must
be known before DB-level enforcement can reliably guard follow-up queries.

Workspace-service aggregate endpoints:

- `/notebooks/{notebookId}`
- `/notebooks/{notebookId}/members`
- `/tags/{tagId}`
- `/notebooks/{notebookId}/tags/{tagId}`
- `/invitations/{invitationId}/revoke`

Content-service aggregate endpoints:

- `/notebooks/{notebookId}/notes`
- `/notes/{noteId}`
- `/notes/{noteId}/versions`
- `/notes/{noteId}/links`
- `/notes/{noteId}/tags`
- `/notes/{noteId}/comments`
- `/comments/{commentId}`

Path/query endpoints with explicit `workspaceId` do not require the header, but if supplied it must
match the resolved workspace.

## Monitoring And Alerts

Recommended production signals:

- `MISSING_WORKSPACE_CONTEXT` rate increase after Stage 1.
- `INVALID_WORKSPACE_CONTEXT` rate increase, especially by route and client version.
- Tenant-related 403/404 spikes after Stage 2 or Stage 4.
- PostgreSQL `permission denied`, `violates row-level security policy` or missing
  `app.current_workspace_id` errors.
- Readiness/liveness failures after switching runtime credentials.
- Audit event gaps for workspace/note operations during rollout windows.

## Rollback Playbook

Rollback in this order unless the incident demands immediate FORCE RLS disablement:

| Step | Action | Risk | Expected effect | Verify |
|---|---|---|---|---|
| 1 | Set `APP_RLS_STRICT_WORKSPACE_HEADER=false` | Low | Aggregate endpoints accept legacy header-less calls again | strict-header 400 rate drops |
| 2 | Set `APP_RLS_ENABLED=false` | Low/medium | DB session tenant setting is no longer applied; app auth remains | DB setting errors stop |
| 3 | Restart affected deployments | Low | Pods pick up config changes | readiness healthy |
| 4 | Run disable FORCE RLS scripts | Medium | Table-owner bypass protection is removed for selected tables | `check-rls-status.sql` shows FORCE false |
| 5 | Restore previous runtime DB credential if grants are broken | Medium/high | Service recovers DML access | smoke test passes |
| 6 | Review audit/logs | None | Incident scope and cross-tenant risk understood | audit timeline complete |

Commands:

```bash
kubectl rollout restart deployment/<workspace-deployment> -n <namespace>
kubectl rollout restart deployment/<content-deployment> -n <namespace>
psql "$DB_MIGRATION_URL" -f scripts/db/disable-force-rls-workspace.sql
psql "$DB_MIGRATION_URL" -f scripts/db/disable-force-rls-content.sql
```

If rollback reaches credential restoration, treat it as an incident. Migration-owner credentials
should be temporary and removed from runtime deployments after grants are repaired.
