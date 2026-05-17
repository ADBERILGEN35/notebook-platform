# Backend production approval gate (Faz 125)

Single go/no-go decision table for backend production **flag enablement**, based on the pre-prod evidence bundle and Faz 124 readiness review.

| Document | Role |
|----------|------|
| [`backend-production-readiness-review.md`](backend-production-readiness-review.md) | Static controls, risks, dangerous defaults |
| [`backend-production-readiness-checklist.md`](backend-production-readiness-checklist.md) | Per-area RC checklist |
| [`backend-preprod-evidence-bundle.md`](backend-preprod-evidence-bundle.md) | Bundle format and builder |
| [`backend-production-flag-flip-plan.md`](backend-production-flag-flip-plan.md) | Wave order after `GO` (Faz 126) |
| [`backend-production-rollback-matrix.md`](backend-production-rollback-matrix.md) | Per-family rollback |
| [`backend-production-change-request-template.md`](backend-production-change-request-template.md) | CR copy-paste template |
| [`backend-staging-pp-evidence-execution-plan.md`](backend-staging-pp-evidence-execution-plan.md) | Staging PP run order, workflows, artifacts (Faz 130) |
| [`backend-staging-pp-secrets-setup.md`](backend-staging-pp-secrets-setup.md) | GitHub secrets + dispatch + download (Faz 133) |
| [`backend-release-candidate-signoff.md`](backend-release-candidate-signoff.md) | RC sign-off package + final decision (Faz 131) |
| [`backend-release-freeze-checklist.md`](backend-release-freeze-checklist.md) | Release freeze process (Faz 131) |
| This doc | **Approval gate rules** and CR attachment list |

---

## When to use

Before enabling any of:

- Break-glass + gateway denylist (`BREAK_GLASS_*`, `GATEWAY_BREAK_GLASS_*`)
- SCIM delta remote fetch / multi-page (`SCIM_DELTA_*`)
- Retention dedicated datasource (`retentionDatasource.*.enabled`)
- Admin GitOps live provider, enforced RBAC (per review § flag families)

Attach to the CR:

1. `backend-preprod-evidence-summary.md` (required)
2. `backend-preprod-evidence-bundle.json` (required)
3. Upstream sanitized artifacts (PP-1..PP-3) referenced by the bundle
4. `backend-rc-readiness-summary.md` (required — Faz 127)
5. `backend-docker-ci-summary.md` (required — Faz 128/129)

Operator runbook: [`backend-staging-pp-evidence-execution-plan.md`](backend-staging-pp-evidence-execution-plan.md). Live run: [`backend-live-staging-pp-evidence-run-checklist.md`](backend-live-staging-pp-evidence-run-checklist.md) + `run-backend-live-staging-pp-evidence.sh --all` (Faz 135).

---

## Go / no-go rules

| # | Rule | `finalRecommendation` |
|---|------|------------------------|
| G1 | PP-1 status is `pass` (SCIM `certified`) | Required for `GO` |
| G2 | PP-2 status is `pass` (drill `passed` + `BREAK_GLASS_TOKEN_REVOKED`) | Required for `GO` |
| G3 | If retention datasource is in production rollout scope, PP-3 status is `pass`; else PP-3 may be `not_required` | Required for `GO` |
| G4 | No `privacy_failure` on any PP or bundle output | Else `NO_GO` |
| G5 | No `shape_mismatch` on inputs | Else `NO_GO` |
| G6 | PP-1 or PP-2 `missing` or `fail` | `NO_GO` |
| G7 | PP-3 required and `missing` or `fail` | `NO_GO` |
| G8 | `acceptedRisks` non-empty or `highRiskCount > 0` with all gates pass | `GO_WITH_ACCEPTED_RISKS` |
| G9 | G1–G7 satisfied, no accepted risks documented in bundle | `GO` |

**Merge / ship artifacts** (chart defaults, unit tests) remains **GO** per Faz 124 — this gate applies to **production flag flip**, not artifact publication.

---

## PP interpretation quick reference

| Gate | Pass | Fail / NO_GO |
|------|------|----------------|
| **PP-1** | `certificationResult: certified` | skipped, needs-review, blocked, missing file, not certified |
| **PP-2** | `result: passed` + gateway `BREAK_GLASS_TOKEN_REVOKED` | skipped, failed, readiness-gap, privacy-failure, shape-mismatch, missing |
| **PP-3** | All domains passed (if required) | missing, skipped, partial fail; or waived → `not_required` |

Set `PREPROD_PP3_REQUIRED=true` (or workflow input `pp3_required`) when dedicated retention datasource is in the production change scope.

---

## Approver matrix (unchanged from Faz 124)

| Flag family | Approvers | Evidence |
|-------------|-----------|----------|
| Break-glass + denylist | Security + on-call lead | PP-2 bundle slice + drill summary |
| SCIM delta remote | Identity + integration owner | PP-1 bundle slice + provider cert doc |
| Retention datasource | DBA + security | PP-3 bundle slice + RLS preflight |
| Admin GitOps live | Platform + security | Separate GitOps checklist (not in PP bundle) |

---

## CR checklist (copy for reviewers)

- [ ] `backend-preprod-evidence-bundle.json` attached; `finalRecommendation` reviewed
- [ ] `backend-preprod-evidence-summary.md` attached
- [ ] PP-1 artifact: `scim-delta-sandbox-evidence.json` (if SCIM flags in scope)
- [ ] PP-2 artifact: `break-glass-revocation-evidence.json` (if break-glass flags in scope)
- [ ] PP-3 artifact: `retention-staging-smoke-results.json` (if datasource in scope)
- [ ] No forbidden patterns in attached markdown/JSON (spot check or CI bundle job)
- [ ] Faz 124 dangerous-defaults CI still green on release branch
- [ ] Rollback steps documented in CR for each flag family enabled

---

## Workflow

Follow [`backend-staging-pp-evidence-execution-plan.md`](backend-staging-pp-evidence-execution-plan.md) for the full sequence (RC/Docker CI → PP-1 → PP-2 → PP-3 if in scope → bundle).

1. Confirm RC + Docker CI artifacts on the release candidate branch.
2. Run staging jobs: **SCIM Delta Readiness**, **Break-glass Revocation Readiness**, **Retention Readiness** (`run_staging_smoke=true` when PP-3 required).
3. Download artifacts; normalize with `scripts/security/prepare-backend-preprod-evidence-inputs.sh` if needed.
4. Run **Backend Pre-prod Evidence Bundle** (`workflow_dispatch`) with correct `scim_artifact_name` and `pp3_required`, or run builder locally.
5. If `NO_GO`, do not enable production flags; fix evidence or scope.
6. If `GO_WITH_ACCEPTED_RISKS`, security sign-off on listed risks required.
7. If `GO`, open CR from [`backend-production-change-request-template.md`](backend-production-change-request-template.md) and execute one wave from [`backend-production-flag-flip-plan.md`](backend-production-flag-flip-plan.md); confirm `validate-production-flag-plan.sh` PASS on the GitOps PR branch before merge.

---

## Exit codes (builder)

| Code | Meaning |
|------|---------|
| 0 | `GO` or `GO_WITH_ACCEPTED_RISKS` |
| 3 | Privacy violation in bundle output |
| 5 | `NO_GO` |
