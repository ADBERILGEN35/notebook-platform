# Workspace Retention RLS Production Runbook (Faz 107)

Faz 106'da workspace-service'e eklenen aggregate-only retention dry-run count akışı (`GET /internal/admin/retention/workspace/plan`) cross-workspace aggregate üretir. Production'da `invitations` tablosunda RLS aktifken standart tenant role ile count'lar yanıltıcı sıfır dönebilir. Bu runbook dedicated DB role, preflight, smoke ve rollout checklist'ini tanımlar.

Destructive purge, invitation delete, membership delete ve workspace metadata delete kapsam dışıdır.

## 1. Amaç ve Kapsam

- Kapsam: `invitations` (expired pending), `workspace_audit_events` (audit-like) üzerinde aggregate-only count.
- Out of scope: `workspace.workspaces` / `notebooks` / `memberships` inventory-only target'ları, destructive purge, tenant policy.

## 2. Neden Normal Tenant RLS Yeterli Değil?

`invitations` workspace policy ile `app.current_workspace_id` olmadan filtrelenir. Retention platform-wide count üretir; tenant role ile eligible count sıfır görünebilir.

## 3. Dedicated Retention DB Role

Önerilen rol: `notebook_workspace_retention`.

- `rolcanlogin = true`, `rolbypassrls = true` (veya `row_security=off` connection option)
- `rolsuper = false`; `SELECT` only on `invitations`, `workspace_audit_events`

```sql
CREATE ROLE notebook_workspace_retention LOGIN PASSWORD '<replace-with-secret>' BYPASSRLS;
GRANT CONNECT ON DATABASE notebook_platform TO notebook_workspace_retention;
GRANT USAGE ON SCHEMA public TO notebook_workspace_retention;
GRANT SELECT ON invitations, workspace_audit_events TO notebook_workspace_retention;
```

## 4. BYPASSRLS vs `row_security=off`

Faz 101 content runbook ile aynı trade-off. Yalnızca retention datasource bu role'ü kullanmalıdır.

## 5. Minimum DB Privilege Set

| Object | Privilege |
|---|---|
| `invitations` | `SELECT` |
| `workspace_audit_events` | `SELECT` |

## 6. Preflight SQL

Script: [`scripts/retention/check-workspace-retention-rls-readiness.sql`](../scripts/retention/check-workspace-retention-rls-readiness.sql)

```bash
psql "$WORKSPACE_DB_URL" -v retention_role=notebook_workspace_retention \
  -f scripts/retention/check-workspace-retention-rls-readiness.sql
```

Read-only; migration owner ile çalıştırın.

## 7. Gateway Smoke Script

Script: [`scripts/retention/workspace-retention-dry-run-smoke.sh`](../scripts/retention/workspace-retention-dry-run-smoke.sh)

```bash
export API_BASE_URL=https://gateway.example.com
export ADMIN_ACCESS_TOKEN='<admin-jwt>'
export EXPECT_WORKSPACE_RETENTION_READY=true
bash scripts/retention/workspace-retention-dry-run-smoke.sh
```

Exit codes: `0` ok; `2` readiness gap (when expect true); `3` privacy; `4` shape.

## 8. Env / Toggle Rollout

- workspace-service: `WORKSPACE_RETENTION_DRY_RUN_COUNTS_ENABLED`
- gateway: `WORKSPACE_RETENTION_INTEGRATION_ENABLED`, `WORKSPACE_RETENTION_INTERNAL_URL`
- Prod default: `false`. Dev/staging GitOps ile `true` olabilir.

## 9. Observability

- Service-local: `workspace_retention_dry_run_total`, `workspace_retention_eligible_count`
- Platform: Faz 105 `platform_retention_service_summary_total{service="workspace-service",...}`

## 10. Rollback

GitOps'ta `workspaceRetentionDryRunCountsEnabled` ve `workspaceRetentionIntegrationEnabled` → `"false"`. Plan generation devam eder; workspace count merge olmaz.

## 11. Güvenlik ve PII

Response/log'da workspace name, invitation email, user email, token hash yok. Backend `WORKSPACE_RETENTION_DB_PERMISSION_DENIED` ile raw SQL sızdırmaz (Faz 106).

## 12. Production Rollout Checklist

- [ ] `notebook_workspace_retention` role + `SELECT` grants
- [ ] Preflight SQL sections 0–7 beklenen sonuç
- [ ] `WORKSPACE_RETENTION_ADMIN_SERVICE_JWT_*` + gateway signing kid
- [ ] Smoke exit 0 with `EXPECT_WORKSPACE_RETENTION_READY=true`
- [ ] Governance change-request approved
- [ ] Rollback PR hazır

## 13. CI (Faz 108 / Faz 109)

- Fixture gate (no secret): `retention-smoke-fixtures` → `test-workspace-search-retention-smoke-fixtures.sh`.
- Live staging (opt-in): `retention-staging-smoke` with secrets `RETENTION_STAGING_API_BASE_URL`, `RETENTION_STAGING_ADMIN_ACCESS_TOKEN`. See [`.github/workflows/retention-readiness.yml`](../.github/workflows/retention-readiness.yml).
- Faz 109: workspace domain satırı job summary ve `retention-staging-smoke-evidence` artifact içinde (sanitized).
- Optional SQL: `check-workspace-retention-rls-readiness.sql` (manual `psql`, not in CI).
