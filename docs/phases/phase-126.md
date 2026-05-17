# Faz 126: Backend Production Flag Flip Plan + Rollback Matrix

## Hedef

Faz 125 pre-prod evidence bundle **`GO`** (veya imzalı **`GO_WITH_ACCEPTED_RISKS`**) olduktan sonra production’da hangi feature flag’lerin hangi sırayla, hangi onay ve rollback adımıyla açılacağını dokümante etmek. **Bu fazda hiçbir production flag açılmaz**; yalnızca plan, matrix, CR template ve CI guardrail.

## Deliverables

| Artifact | Path |
|----------|------|
| Flag flip plan | `docs/backend-production-flag-flip-plan.md` |
| Rollback matrix | `docs/backend-production-rollback-matrix.md` |
| Change request template | `docs/backend-production-change-request-template.md` |
| Validator | `scripts/security/validate-production-flag-plan.sh` |
| CI | `scripts/security/ci-production-flag-plan.sh` |

## Non-goals

Production flag açma, scheduler, provider mutation, destructive purge, break-glass admin write unblock, auto-merge, runtime kod, GitOps prod enable commit.

## Prerequisites

- [`backend-preprod-evidence-bundle.json`](../backend-preprod-evidence-bundle.md) → `finalRecommendation: GO`
- [`backend-production-approval-gate.md`](../backend-production-approval-gate.md) sign-off

Detay: [`phase-126-summary.md`](phase-126-summary.md).
