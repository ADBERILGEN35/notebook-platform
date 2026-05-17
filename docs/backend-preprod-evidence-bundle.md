# Backend pre-prod evidence bundle (Faz 125)

Sanitized aggregate of staging evidence gates **PP-1**, **PP-2**, and **PP-3** for release-candidate approval. No tokens, JWTs, JDBC URLs, raw SCIM bodies, cursors, or emergency credentials appear in bundle outputs.

Related: [`backend-production-approval-gate.md`](backend-production-approval-gate.md), [`backend-production-readiness-review.md`](backend-production-readiness-review.md), [`backend-staging-pp-evidence-execution-plan.md`](backend-staging-pp-evidence-execution-plan.md) (Faz 130 operator runbook).

---

## Purpose

Operators run upstream readiness workflows on staging, collect JSON artifacts, then build one bundle for the change request (CR):

1. Single JSON for automation and audit.
2. Single markdown summary for reviewers.
3. Explicit `missing` / `fail` flags — never hidden.

---

## Builder

```bash
export PREPROD_SCIM_EVIDENCE_PATH=/path/to/scim-delta-sandbox-evidence.json
export PREPROD_BREAK_GLASS_EVIDENCE_PATH=/path/to/break-glass-revocation-evidence.json
export PREPROD_RETENTION_EVIDENCE_PATH=/path/to/retention-staging-smoke-results.json  # if PP-3 required
export PREPROD_PP3_REQUIRED=false   # true when retentionDatasource is in prod rollout scope
export RELEASE_CANDIDATE_ID=rc-2026-05-17
export GIT_SHA=abc123
export IMAGE_TAG=staging-20260517

bash scripts/security/build-backend-preprod-evidence-bundle.sh
```

Outputs (default dir `backend-preprod-evidence-out/`):

- `backend-preprod-evidence-bundle.json`
- `backend-preprod-evidence-summary.md`

GitHub Actions: **Backend Pre-prod Evidence Bundle** workflow (`workflow_dispatch` only). Artifact name: `backend-preprod-evidence-bundle`.

---

## Aggregate schema (`backend-preprod-evidence-bundle-v1`)

| Field | Type | Description |
|-------|------|-------------|
| `evidenceSchemaVersion` | string | `backend-preprod-evidence-bundle-v1` |
| `releaseCandidateId` | string | RC identifier |
| `gitSha` | string | Build / deploy SHA |
| `imageTag` | string | Container tag under review |
| `environment` | string | Typically `staging` |
| `generatedAt` | string | ISO-8601 UTC |
| `pp1Scim` | object | PP-1 normalized status |
| `pp2BreakGlass` | object | PP-2 normalized status |
| `pp3Retention` | object | PP-3 normalized status |
| `blockerCount` | number | Gates blocking release |
| `highRiskCount` | number | Documented high risks (from env/input) |
| `acceptedRisks` | string[] | Explicit accepted risk notes |
| `missingEvidence` | string[] | e.g. `PP-1:missing` |
| `privacyViolation` | boolean | Any input or output privacy failure |
| `shapeMismatch` | boolean | Invalid input JSON |
| `finalRecommendation` | enum | `GO` \| `NO_GO` \| `GO_WITH_ACCEPTED_RISKS` |

### PP status values

| Status | Meaning |
|--------|---------|
| `pass` | Gate satisfied |
| `fail` | Evidence present but criteria not met |
| `missing` | File absent or upstream skipped |
| `not_required` | PP-3 waived (datasource not in rollout scope) |
| `privacy_failure` | Upstream or bundle privacy check failed |
| `shape_mismatch` | JSON parse / schema failure |

---

## Input formats (normalized)

### PP-1 — `scim-delta-sandbox-evidence.json`

Schema: `scim-delta-evidence-v1` (see [`scim-delta-sandbox-evidence.md`](scim-delta-sandbox-evidence.md)).

| Pass rule | `certificationResult == "certified"` |
| Fail / review | `skipped`, `needs-review`, `blocked`, absent, or other |
| Privacy | `evidenceStatus == "privacy_violation"` → bundle `NO_GO` |

Sanitized fields only in upstream artifact (provider type, strategy, counts, certification hints). No `Authorization`, raw body, or `nextCursor`.

### PP-2 — `break-glass-revocation-evidence.json`

Schema: `break-glass-revocation-evidence-v1` (see [`break-glass-revocation-staging-drill.md`](break-glass-revocation-staging-drill.md)).

| Pass rule | `result == "passed"` and `gatewayRejectErrorCode == "BREAK_GLASS_TOKEN_REVOKED"` |
| Fail | `failed`, `readiness-gap`, `shape-mismatch`, `privacy-failure`, `skipped` |
| Missing | File not provided |

Masked identifiers only (`jtiMasked`, `sessionIdShort`). No emergency token or JWT material.

### PP-3 — `retention-staging-smoke-results.json`

Version `1` (see [`retention-datasource-ops-handoff.md`](retention-datasource-ops-handoff.md)).

| Not required | `PREPROD_PP3_REQUIRED=false` (default) — retention datasource not in production rollout |
| Pass (required) | `overall.status == "passed"` and every `domains[].status == "passed"` |
| Fail | `skipped`, `readiness-gap`, partial domain failure, missing file |

---

## Privacy validation

Builder runs forbidden-pattern grep on JSON + summary:

- `Bearer`, `eyJ`, `access_token`, `Authorization`, `password`, `secret`, `jdbc:`, `rawPayload`, `nextCursor`, `@odata.nextLink`, `emergency token`

Match → `privacyViolation: true`, `finalRecommendation: NO_GO`, exit code `3`.

---

## Fixture inputs (CI)

`scripts/security/fixtures/preprod-input/` — synthetic pass samples for CI only, not live staging evidence.

---

## Mapping from Faz 124 verdict

| Faz 124 | Faz 125 bundle |
|---------|----------------|
| `READY_WITH_PREPROD_BLOCKERS` | Expected when live inputs missing → `NO_GO` |
| PP-1..PP-3 pending | `missingEvidence` lists each gate |
| Repo defaults safe | Unchanged; bundle does not re-run chart checks |

Complete PP evidence on staging → re-run builder → expect `GO` (or `GO_WITH_ACCEPTED_RISKS` if risks documented).
