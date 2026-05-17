# Retention Staging E2E — Evidence Formats (Faz 114)

Sanitized evidence only. **Do not** attach JDBC URLs, credentials, tokens, raw actuator JSON, raw SQL output, or API response bodies to change requests or public tickets.

Reference: [`staging-dedicated-retention-e2e-checklist.md`](staging-dedicated-retention-e2e-checklist.md), [`staging-retention-change-request-evidence-checklist.md`](staging-retention-change-request-evidence-checklist.md).

---

## 1. Actuator retention datasource health

Per-service Spring Boot Actuator component (Faz 113). Collect via authorized `GET /actuator/health` (or cluster tooling that extracts component status). **Record summary only.**

| Service | Component id | Allowed summary values |
|---------|--------------|------------------------|
| content | `contentRetentionDataSourceHealth` | `UP`, `DOWN`, `DISABLED`, `FALLBACK_PRIMARY`, `NOT_CONFIGURED` |
| notification | `notificationRetentionDataSourceHealth` | same |
| workspace | `workspaceRetentionDataSourceHealth` | same |
| search | `searchRetentionDataSourceHealth` | same |

### Ticket / CR field format

```text
Actuator retention datasource (staging):
  content: UP | usingDedicated=true | warnings=none
  notification: UP | usingDedicated=true | warnings=none
  workspace: UP | usingDedicated=true | warnings=none
  search: UP | usingDedicated=true | warnings=none
```

Use `lastCheckStatus` from safe details when available. Map component aggregate: `UP`/`DOWN` for health contributor; `DISABLED` / `FALLBACK_PRIMARY` for informational states.

### Symbolic warnings only (if any)

| Code | Meaning |
|------|---------|
| `RETENTION_DATASOURCE_DISABLED` | `enabled=false` |
| `RETENTION_DATASOURCE_NOT_CONFIGURED` | incomplete config (should not run in prod traffic) |
| `RETENTION_DATASOURCE_USING_PRIMARY_FALLBACK` | no dedicated bean |
| `RETENTION_DATASOURCE_CONNECTION_FAILED` | dedicated pool connectivity failed |

**Forbidden in evidence:** `jdbc:`, hostnames, usernames, passwords, `SQLException` text, full health JSON.

### Post-rollback expected

```text
  content: DISABLED | fallbackToPrimary=true
  (repeat for notification, workspace, search)
```

---

## 2. Gateway smoke evidence (Faz 109)

Produced by `run-retention-staging-smoke.sh` or CI job `retention-staging-smoke`.

### Artifact bundle (attach to CR)

| File | Role |
|------|------|
| `retention-staging-smoke-results.json` | Machine-readable sanitized domain table |
| `retention-staging-smoke-summary.md` | Human-readable same content |

CI artifact name: **`retention-staging-smoke-evidence`**.

### JSON schema (version 1)

```json
{
  "version": 1,
  "generatedAt": "2026-05-17T12:00:00Z",
  "stagingSecretsConfigured": true,
  "overall": {
    "status": "passed",
    "exitCode": 0,
    "message": "All domains passed or expected-gap."
  },
  "domains": [
    {
      "domain": "content",
      "status": "passed",
      "exitCode": 0,
      "expectReady": true,
      "message": "Smoke completed successfully."
    }
  ]
}
```

### Domain `status` values (allowed in evidence)

| Status | Meaning |
|--------|---------|
| `passed` | Exit 0, production-ready signal |
| `expected-gap` | Exit 0, pre-rollout / not enabled (rollback evidence) |
| `readiness-gap` | Exit 2 |
| `privacy-failure` | Exit 3 — **blocks green** |
| `shape-failure` | Exit 4 — **blocks green** |
| `skipped` | Missing staging secrets (CI only; not a green enable rollout) |

### CR smoke summary line format

```text
Smoke (staging, EXPECT_*=true):
  content: passed (exit 0)
  notification: passed (exit 0)
  workspace: passed (exit 0)
  search: passed (exit 0)
  overall: passed (exit 0)
```

Pre-enable or post-rollback:

```text
  content: expected-gap (exit 0)
  overall: passed (exit 0) — documented pre-rollout gap
```

Validation: `bash scripts/retention/validate-retention-staging-artifact.sh`

---

## 3. GitHub Actions Step Summary

Rendered by `render-retention-staging-github-summary.sh` into `$GITHUB_STEP_SUMMARY`.

### Format

```markdown
## Retention staging smoke

**Overall:** `passed` (exit `0`) — All domains passed or expected-gap.

| Domain | Status | Exit | Expect | Message |
|--------|--------|------|--------|---------|
| content | `passed` | 0 | true | Smoke completed successfully. |
```

**CR evidence:** screenshot or copy the summary table only (no workflow logs with env vars). Link to workflow run ID + artifact name.

---

## 4. Preflight SQL evidence

Scripts (read-only, migration owner):

1. `check-retention-rls-readiness.sql` → **content**
2. `check-notification-retention-rls-readiness.sql` → **notification**
3. `check-workspace-retention-rls-readiness.sql` → **workspace**
4. `check-search-retention-rls-readiness.sql` → **search**

### CR format (PASS/FAIL only)

```text
Preflight SQL (staging DB, read-only):
  content: pass
  notification: pass
  workspace: pass
  search: pass
```

On **fail**: note `fail` + one-line reason code (e.g. `missing SELECT on table X`) — **not** full `psql` output, role names with passwords, or connection strings.

---

## 5. Metrics / dashboard evidence

Dashboard UID: `platform-retention-readiness`  
Metric (primary): `platform_retention_service_summary_total{service, status}`

### Panels to review (screenshot titles in CR)

| Panel | PromQL / intent | Evidence note |
|-------|-----------------|-----------------|
| Retention service status by service | `platform_retention_service_summary_total` by `status` | Record dominant `status` per service: `READY`, `UNAVAILABLE`, etc. |
| Last known readiness by service / status | instant table | Same, 6h window |
| Service unavailable warning count | `platform_retention_service_warnings_total{warning_code="SERVICE_UNAVAILABLE"}` | `none` or `elevated` per service |
| Capped count warning count | `warning_code="COUNT_CAPPED"` | `none` or `review` |
| Legal-hold blocked target count | `platform_retention_service_blocked_targets_total` | `none` or `informational` |

### CR format

```text
Grafana platform-retention-readiness (staging, post-smoke):
  content-service: status=READY; unavailable=none; capped=none; blocked-by-hold=none
  notification-service: status=READY; unavailable=none; capped=none; blocked-by-hold=none
  workspace-service: status=READY; unavailable=none; capped=none; blocked-by-hold=none
  search-service: status=READY; unavailable=none; capped=none; blocked-by-hold=none
```

Alerts (informational): `PlatformRetentionServiceUnavailable`, `PlatformRetentionCappedCounts`, `PlatformRetentionBlockedByLegalHoldHigh` — note firing yes/no only.

**Forbidden:** high-cardinality labels, user/workspace/note identifiers in screenshots.

---

## 6. Rollback evidence

After overlay revert + Argo sync + pod restart:

| Check | Expected summary |
|-------|------------------|
| Git | Overlay reverted; `retentionDatasource.*.enabled: false` in effective values |
| Pod env | `*_RETENTION_DATASOURCE_ENABLED=false` (presence only, no values logged) |
| Actuator | four × `DISABLED`, `RETENTION_DATASOURCE_DISABLED` |
| Smoke | domains `expected-gap` or integration off; overall exit 0 with documented message |
| Metrics | `UNAVAILABLE` not sustained (optional re-check) |

### CR format

```text
Rollback drill (staging): completed=yes
  overlay reverted: yes
  pods restarted: yes
  actuator post-rollback: all DISABLED
  smoke post-rollback: expected-gap (exit 0)
```

---

## 7. Deployment metadata (no secrets)

```text
Environment: staging
Git SHA: <40-char commit>
Image tag: <chart appVersion or digest>
Argo CD revision: <app revision>
Retention overlay commit: <commit or PR that merged enable overlay>
ExternalSecret synced: yes | no
```

Do not paste `ADMIN_ACCESS_TOKEN`, `RETENTION_STAGING_*`, or Kubernetes secret data.
