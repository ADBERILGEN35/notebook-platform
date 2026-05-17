# Staging Dedicated Retention Datasource — Change Request Evidence Checklist (Faz 114)

Attach **sanitized summaries only** to the change request. Full formats: [`retention-staging-e2e-evidence-formats.md`](retention-staging-e2e-evidence-formats.md).

Generate a blank copy: `bash scripts/retention/generate-retention-e2e-evidence-template.sh > evidence.md`

---

## Identity & deployment

| Field | Value |
|-------|--------|
| Change request ID | |
| Environment | staging |
| Git SHA / image tag | |
| Argo CD sync revision | |
| Retention datasource overlay commit (enable merge) | |
| ExternalSecret synced | yes / no |
| Executor / date | |

---

## Preflight SQL summary (PASS/FAIL only — no raw SQL in ticket)

| Domain | Result |
|--------|--------|
| content | pass / fail |
| notification | pass / fail |
| workspace | pass / fail |
| search | pass / fail |

Failure notes (one line, no connection strings):

---

## Actuator health summary (`lastCheckStatus`)

| Service | Status |
|---------|--------|
| content (`contentRetentionDataSourceHealth`) | UP / DOWN / DISABLED / FALLBACK_PRIMARY / NOT_CONFIGURED |
| notification (`notificationRetentionDataSourceHealth`) | |
| workspace (`workspaceRetentionDataSourceHealth`) | |
| search (`searchRetentionDataSourceHealth`) | |

Dedicated pool in use (when enabled): yes / no per service  
Symbolic warnings (if any):

---

## Smoke summary

Attach artifact: **`retention-staging-smoke-evidence`** (`retention-staging-smoke-results.json` + `retention-staging-smoke-summary.md`)

| Domain | Status | Exit | Expect ready |
|--------|--------|------|--------------|
| content | passed / expected-gap / readiness-gap / privacy-failure / shape-failure / skipped | | true / false |
| notification | | | |
| workspace | | | |
| search | | | |

Overall smoke: | passed / failed / skipped | Exit: |

GitHub Actions run URL (optional):  
Step Summary captured: yes / no

---

## Grafana / Prometheus summary

Dashboard: `platform-retention-readiness`

| Service | Readiness status | UNAVAILABLE | COUNT_CAPPED | Blocked-by-hold |
|---------|------------------|-------------|--------------|-----------------|
| content-service | READY / other | none / elevated | none / review | none / informational |
| notification-service | | | | |
| workspace-service | | | | |
| search-service | | | | |

Alert firing (yes/no only): `PlatformRetentionServiceUnavailable` | `PlatformRetentionCappedCounts` | `PlatformRetentionBlockedByLegalHoldHigh`

Screenshot filenames (stored in CR attachment system, not repo):

---

## Rollback

| Item | Value |
|------|--------|
| Rollback drill tested | yes / no |
| Overlay reverted | yes / no / n/a |
| Pods restarted after revert | yes / no |
| Post-rollback actuator | all DISABLED / other |
| Post-rollback smoke | expected-gap / skipped / other |

---

## Acceptance (staging green)

- [ ] All preflight **pass**
- [ ] All four actuator summaries **UP** with dedicated pool (when rollout goal is enable)
- [ ] All four smoke domains **passed** with `EXPECT_*=true`, overall exit **0**
- [ ] No `privacy-failure` or `shape-failure` in smoke artifact
- [ ] Smoke artifact validated (`validate-retention-staging-artifact.sh`)
- [ ] Metrics: no sustained `UNAVAILABLE`; capped/hold warnings reviewed
- [ ] ExternalSecret synced before enable
- [ ] Rollback drill documented (recommended before prod promotion)

---

## Approval notes

Reviewer sign-off:

Exceptions / follow-ups:
