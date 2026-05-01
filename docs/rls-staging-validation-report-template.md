# RLS Staging Validation Report Template

## Environment

- Date:
- Operator:
- Environment:
- Git commit:
- Image tags/digests:
- Database:
- Services validated:

## Configuration

- `APP_RLS_STRICT_WORKSPACE_HEADER`:
- `APP_RLS_ENABLED`:
- Runtime DB role:
- Migration DB role:
- FORCE RLS enabled:

## DB Role Check Output

Attach or paste summarized output from:

```bash
DB_URL=<postgres-url> DB_USER=<inspection-user> DB_PASSWORD=<secret> \
  bash scripts/rls/run-preflight-checks.sh
```

Required result:

- runtime role is not table owner
- runtime role is not superuser
- runtime role does not have `BYPASSRLS`
- runtime role cannot create tables
- tenant-scoped tables have RLS enabled

## RLS Status Output

- Tables with RLS enabled:
- Tables with FORCE RLS enabled:
- Unexpected tables:
- Missing policies:

## FORCE RLS Enabled Tables

Workspace-service:

- `workspace_members`
- `notebooks`
- `notebook_members`
- `tags`
- `notebook_tags`
- `invitations`

Content-service:

- `notes`
- `note_versions`
- `note_links`
- `comments`
- `note_tags`

## Smoke Test Result

- `scripts/smoke-test.sh`:
- `scripts/smoke-test-auth-security.sh`:
- `STRICT_MODE_EXPECTED=true scripts/smoke-test-rls-strict.sh`:

## Cross-Tenant Test Result

Workspace-service:

- notebook cross-read:
- tag cross-read:
- invitation revoke wrong workspace:
- `GET /workspaces` user-level endpoint:

Content-service:

- note cross-read:
- note version cross-read:
- comment cross-read:
- note tag cross-read:
- links/backlinks:
- search:

## Rollback Test Result

- `scripts/rls/run-force-rls-disable.sh`:
- FORCE RLS status after rollback:
- `APP_RLS_ENABLED=false` safe-mode smoke result:
- `APP_RLS_STRICT_WORKSPACE_HEADER=false` compatibility check:

## Observability

- Missing workspace context rate:
- Invalid workspace context rate:
- 403 rate:
- 5xx DB permission symptoms:
- content-service workspace permission unavailable symptoms:
- Log query links:

## Approval Checklist

- [ ] Runtime/migration role split verified.
- [ ] Strict header validation passed.
- [ ] Tenant context validation passed.
- [ ] FORCE RLS enable and disable tested.
- [ ] Cross-tenant negative tests passed.
- [ ] Rollback owner and window identified.
- [ ] Alerts/log queries watched during validation.
- [ ] Production rollout explicitly not performed by this validation.
