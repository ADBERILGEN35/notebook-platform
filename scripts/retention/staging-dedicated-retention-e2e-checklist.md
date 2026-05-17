# Staging Dedicated Retention Datasource — E2E Checklist (Faz 112–114)

Use after Faz 111 runtime binding is deployed. **No real credentials in this repo.** Staging GitOps keeps `retentionDatasource.*.enabled: false` until secret manager keys exist.

**Faz 114:** evidence collection, change-request bundle, and **green** acceptance criteria — see [`retention-staging-e2e-evidence-formats.md`](retention-staging-e2e-evidence-formats.md) and [`staging-retention-change-request-evidence-checklist.md`](staging-retention-change-request-evidence-checklist.md).

---

## Two rollout paths

| Path | When | GitOps |
|------|------|--------|
| **Conservative default** | Always safe | `deploy/gitops/environments/staging/values.yaml` → all `retentionDatasource.*.enabled: false` |
| **Enable overlay** | After DBA roles + ExternalSecret keys | Merge [`retention-datasource-enable.overlay.example.yaml`](../../deploy/gitops/environments/staging/retention-datasource-enable.overlay.example.yaml) |

Dedicated pool vs primary:

- **Retention count repositories** → dedicated Hikari when `*_RETENTION_DATASOURCE_ENABLED=true`
- **JPA / Flyway / normal API** → unchanged primary `DB_*` / `DB_RUNTIME_*`

---

## Preconditions

- [ ] Staging dry-run + gateway integration flags on (content/notification/workspace/search)
- [ ] DBA roles created (docs only SQL in [`retention-datasource-ops-handoff.md`](../../docs/retention-datasource-ops-handoff.md))
- [ ] ExternalSecret keys in `notebook-platform-secrets` (see [`externalsecret-retention-keys.example.yaml`](../../deploy/gitops/environments/staging/externalsecret-retention-keys.example.yaml))
- [ ] **Do not** set `enabled: true` until keys exist (pods fail-fast on missing URL/username/password)
- [ ] Blank evidence template ready: `bash scripts/retention/generate-retention-e2e-evidence-template.sh > /tmp/retention-evidence.md`

---

## Staging “green” acceptance criteria (Faz 114)

Rollout is **green** when all apply (sanitized evidence recorded in change request):

| # | Gate | Evidence type |
|---|------|----------------|
| G1 | Enable overlay merged; Argo sync healthy; ExternalSecret **synced** | Deployment metadata |
| G2 | Preflight SQL: content, notification, workspace, search → **pass** | PASS/FAIL table only |
| G3 | Pod env: `*_RETENTION_DATASOURCE_ENABLED=true` (names only, no values in ticket) | kubectl note |
| G4 | Actuator: four components `lastCheckStatus=**UP**`, dedicated pool in use | Summary table |
| G5 | Smoke: four domains **passed**, `EXPECT_*=true`, overall exit **0** | JSON + MD artifact |
| G6 | Artifact privacy scan passes | `validate-retention-staging-artifact.sh` |
| G7 | Grafana: no sustained `UNAVAILABLE`; capped/hold reviewed | Panel summary table |
| G8 | Rollback drill completed (recommended) | DISABLED + smoke expected-gap |

**Blocks green:** smoke `privacy-failure` (exit 3), `shape-failure` (exit 4), any preflight **fail**, any actuator **DOWN** after enable, missing ExternalSecret sync.

---

## Ordered verification (after enable overlay applied)

### 1. Helm render

```bash
bash scripts/helm-template-check.sh
helm template notebook-platform deploy/helm/notebook-platform \
  -f deploy/gitops/environments/staging/values.yaml \
  -f deploy/gitops/environments/staging/retention-datasource-enable.overlay.example.yaml \
  | grep -E 'RETENTION_DATASOURCE_ENABLED|content-retention-datasource'
```

Expect `CONTENT_RETENTION_DATASOURCE_ENABLED` value `"true"` on content deployment; no password literals in rendered YAML.

**Evidence:** note “Helm render OK, no password literals” in CR (no rendered YAML attachment).

---

### 2. Preflight SQL (read-only, migration owner)

Order:

1. `scripts/retention/check-retention-rls-readiness.sql` → record **content: pass/fail**
2. `scripts/retention/check-notification-retention-rls-readiness.sql` → **notification**
3. `scripts/retention/check-workspace-retention-rls-readiness.sql` → **workspace**
4. `scripts/retention/check-search-retention-rls-readiness.sql` → **search**

**Evidence:** PASS/FAIL summary only — see [Preflight format](retention-staging-e2e-evidence-formats.md#4-preflight-sql-evidence). **Do not** attach raw `psql` output.

---

### 3. Pod env check (staging cluster)

```bash
# Example — adjust namespace/deployment names
kubectl -n notebook-staging exec deploy/notebook-platform-content -- env \
  | grep CONTENT_RETENTION_DATASOURCE
```

Expect `CONTENT_RETENTION_DATASOURCE_ENABLED=true`; confirm variable **names** exist — do not log URL/username/password in tickets.

**Evidence:** “ENABLED=true present (values not recorded)” per service.

---

### 3b. Actuator retention datasource health (Faz 113)

From an authorized client (port-forward or internal ingress), check each service `/actuator/health` for the retention component.

| Service | Component id | `enabled=false` expect | `enabled=true` + secrets expect |
|---------|--------------|------------------------|----------------------------------|
| content | `contentRetentionDataSourceHealth` | `DISABLED` | `UP`, dedicated=true |
| notification | `notificationRetentionDataSourceHealth` | `DISABLED` | `UP` |
| workspace | `workspaceRetentionDataSourceHealth` | `DISABLED` | `UP` |
| search | `searchRetentionDataSourceHealth` | `DISABLED` | `UP` |

**Evidence:** [Actuator format](retention-staging-e2e-evidence-formats.md#1-actuator-retention-datasource-health) — summary table only, no raw JSON.

---

### 4. Gateway smoke

```bash
export API_BASE_URL=https://api.staging.example.com
export ADMIN_ACCESS_TOKEN='<from secret manager — do not commit>'
export EXPECT_CONTENT_RETENTION_READY=true
export EXPECT_NOTIFICATION_RETENTION_READY=true
export EXPECT_WORKSPACE_RETENTION_READY=true
export EXPECT_SEARCH_RETENTION_READY=true
bash scripts/retention/run-retention-staging-smoke.sh
RETENTION_STAGING_SMOKE_OUTPUT_DIR=retention-staging-smoke-out \
  bash scripts/retention/validate-retention-staging-artifact.sh
```

CI equivalent: GitHub Actions workflow `Retention Readiness` → `workflow_dispatch` → `run_staging_smoke=true` (requires `RETENTION_STAGING_API_BASE_URL`, `RETENTION_STAGING_ADMIN_ACCESS_TOKEN`).

**Evidence:** attach `retention-staging-smoke-results.json` + `retention-staging-smoke-summary.md` — [Smoke format](retention-staging-e2e-evidence-formats.md#2-gateway-smoke-evidence-faz-109).

---

### 5. Faz 109 smoke evidence artifact (CI)

After CI or local smoke:

- Artifact: `retention-staging-smoke-evidence`
- Files: `retention-staging-smoke-results.json`, `retention-staging-smoke-summary.md`
- Job summary table: domain × status × exit × expect × sanitized message

**Evidence:** [GitHub Step Summary format](retention-staging-e2e-evidence-formats.md#3-github-actions-step-summary) — screenshot or workflow link + artifact.

---

### 6. serviceSummaries (gateway plan)

Call `GET /admin/retention/platform/plan?dryRun=true` with admin JWT. Confirm `serviceSummaries` for four services — aggregate counts only.

**Evidence:** “plan dryRun OK, four serviceSummaries present” — no response body in ticket.

---

### 7. Grafana metrics

Dashboard: `platform-retention-readiness` (uid `platform-retention-readiness`).

Review:

- `platform_retention_service_summary_total{service, status}`
- Panels: unavailable / capped / blocked-by-hold warnings

**Evidence:** [Metrics format](retention-staging-e2e-evidence-formats.md#5-metrics--dashboard-evidence) — per-service READY/none/review summary.

Validate repo assets (local/CI): `bash scripts/retention/validate-retention-observability.sh`

---

### 8. Rollback drill

1. `retentionDatasource.*.enabled: false` (revert overlay)
2. `*_RETENTION_INTEGRATION_ENABLED: false` (if needed)
3. `*_RETENTION_DRY_RUN_*_ENABLED: false` (if needed)
4. Argo sync; verify pods restart
5. Actuator → four × `DISABLED`
6. Smoke with `EXPECT_*=false` or documented **expected-gap**

**Evidence:** [Rollback format](retention-staging-e2e-evidence-formats.md#6-rollback-evidence).

---

## Change request bundle (attach list)

1. Completed [`staging-retention-change-request-evidence-checklist.md`](staging-retention-change-request-evidence-checklist.md) (or generated template filled in)
2. `retention-staging-smoke-evidence` artifact (JSON + MD)
3. Optional: Grafana panel screenshots (titles only in filename; no PII)
4. Optional: GitHub Actions run link + Step Summary capture
5. **Do not attach:** secrets, tokens, JDBC strings, raw SQL, raw actuator JSON, API JSON bodies

---

## Related docs

- [`retention-datasource-ops-handoff.md`](../../docs/retention-datasource-ops-handoff.md)
- [`docs/phases/phase-112.md`](../../docs/phases/phase-112.md)
- [`docs/phases/phase-113.md`](../../docs/phases/phase-113.md)
- [`docs/phases/phase-114.md`](../../docs/phases/phase-114.md)
