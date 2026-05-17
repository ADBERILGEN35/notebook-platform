# Faz 125: Backend Pre-prod Evidence Bundle + Approval Gate

## Hedef

PP-1 (SCIM delta sandbox), PP-2 (break-glass revocation drill) ve PP-3 (retention staging smoke) kanıtlarını tek bir **sanitized** release evidence paketinde birleştirmek; production flag flip öncesi CR’ye eklenecek tek özet, tek checklist ve tek go/no-go tablosu üretmek.

## Deliverables

| Artifact | Path |
|----------|------|
| Bundle format | `docs/backend-preprod-evidence-bundle.md` |
| Approval gate | `docs/backend-production-approval-gate.md` |
| Builder script | `scripts/security/build-backend-preprod-evidence-bundle.sh` |
| Python aggregator | `scripts/security/build_backend_preprod_evidence_bundle.py` |
| CI fixtures | `scripts/security/fixtures/preprod-input/` |
| CI script | `scripts/security/ci-backend-preprod-evidence-bundle.sh` |
| Workflow (dispatch only) | `.github/workflows/backend-preprod-evidence-bundle.yml` |
| CI step | `.github/workflows/ci.yml` |

## Input artifacts (normalized filenames)

| PP | File | Source workflow |
|----|------|-----------------|
| PP-1 | `scim-delta-sandbox-evidence.json` | `scim-delta-readiness.yml` |
| PP-2 | `break-glass-revocation-evidence.json` | `break-glass-revocation-readiness.yml` |
| PP-3 | `retention-staging-smoke-results.json` | `retention-readiness.yml` |

## Output artifacts

| File | Purpose |
|------|---------|
| `backend-preprod-evidence-bundle.json` | Machine-readable aggregate + `finalRecommendation` |
| `backend-preprod-evidence-summary.md` | CR handoff markdown (sanitized) |

## Non-goals

Production flag açma, scheduler, provider mutation, break-glass/retention production enable, destructive purge, runtime kod değişikliği, yeni microservice, secret hardcode.

## Approval gate (summary)

| Condition | Recommendation |
|-----------|----------------|
| PP-1 missing / not certified | `NO_GO` |
| PP-2 missing / not passed | `NO_GO` |
| PP-3 required and missing / not passed | `NO_GO` |
| Privacy violation or shape mismatch | `NO_GO` |
| All required pass + documented accepted risks | `GO_WITH_ACCEPTED_RISKS` |
| All required pass, no accepted risks | `GO` |

Detay: [`backend-production-approval-gate.md`](../backend-production-approval-gate.md).

Detay rapor: [`phase-125-summary.md`](phase-125-summary.md).
