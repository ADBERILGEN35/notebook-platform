# Full platform release candidate sign-off (Faz 148)

Single **signable** package that aggregates **backend** and **frontend** release readiness into one **platform** decision: **GO**, **NO_GO**, or **GO_WITH_ACCEPTED_RISKS**.

**Does not enable production feature flags.** Authorizes coordinated rollout only when both domain sign-offs and this platform decision allow it, via approved change requests per [backend-production-flag-flip-plan.md](backend-production-flag-flip-plan.md).

Related:

| Document | Role |
|----------|------|
| [platform-release-go-no-go-checklist.md](platform-release-go-no-go-checklist.md) | Pre-decision attachment + ops gates |
| [backend-release-candidate-signoff.md](backend-release-candidate-signoff.md) | Backend domain rules + PP evidence |
| [frontend-release-candidate-signoff.md](frontend-release-candidate-signoff.md) | Frontend domain rules + visual QA |
| [frontend-release-visual-qa-checklist.md](frontend-release-visual-qa-checklist.md) | Manual frontend QA |
| [backend-release-freeze-checklist.md](backend-release-freeze-checklist.md) | Backend freeze process |
| [backend-production-readiness-review.md](backend-production-readiness-review.md) | Backend blockers / high risks |
| [backend-production-flag-flip-plan.md](backend-production-flag-flip-plan.md) | Wave order |
| [backend-production-rollback-matrix.md](backend-production-rollback-matrix.md) | Rollback procedures |

Generate a blank platform sign-off:

```bash
bash scripts/security/generate-platform-rc-signoff-template.sh \
  --rc-id platform-rc-2026-05-19 \
  --output platform-rc-signoff.md
```

---

## When to use

1. Backend and frontend RC lines are frozen (no unapproved features on the RC SHA).
2. Automated gates have run on the **same** RC SHA (or documented waiver with risk owner).
3. Domain sign-offs are filled from sanitized artifacts — **no secrets in any attachment**.

---

## Required artifacts (platform bundle)

| # | Artifact | Domain | Source |
|---|----------|--------|--------|
| 1 | `backend-rc-readiness-summary.md` (+ JSON) | Backend | [backend-rc-readiness.yml](../.github/workflows/backend-rc-readiness.yml) |
| 2 | `backend-docker-ci-summary.md` (+ JSON) | Backend | [backend-docker-ci.yml](../.github/workflows/backend-docker-ci.yml) |
| 3 | `backend-preprod-evidence-summary.md` (+ bundle JSON) | Backend | Pre-prod bundle / live PP orchestrator |
| 4 | `frontend-rc-readiness-summary.md` (+ JSON) | Frontend | [frontend-rc-readiness.yml](../.github/workflows/frontend-rc-readiness.yml) |
| 5 | Completed [frontend-release-visual-qa-checklist.md](frontend-release-visual-qa-checklist.md) | Frontend | Manual QA |
| 6 | Signed [platform-release-go-no-go-checklist.md](platform-release-go-no-go-checklist.md) | Platform | Release manager |
| 7 | Domain sign-offs (optional copies) | Both | Backend + frontend template outputs |

---

## Package sections

| Section | Content |
|---------|---------|
| Release candidate ID | Platform RC id (may match backend `rc-*` and frontend `fe-rc-*`) |
| Git SHA / image tags / frontend build | Same SHA for monorepo; backend image tags + frontend dist ref |
| Backend readiness summary | RC + Docker + PP aggregate; link to readiness review |
| Frontend readiness summary | RC gate + visual QA + domain status |
| Backend RC / Docker CI status | Verdicts from artifacts |
| Frontend RC gate status | Verdict from artifact |
| Backend PP evidence status | PP-1, PP-2, PP-3; bundle `finalRecommendation` |
| Frontend visual QA status | Complete / incomplete |
| Production flag plan reference | Wave list for first production wave |
| Rollback matrix reference | Owners + procedures |
| Known blockers | Open items blocking GO |
| High risks | From readiness review + RC-specific |
| Accepted risks | Explicit waivers |
| Required approvers | Platform, security, backend, frontend, QA |
| Final decision | **GO** / **NO_GO** / **GO_WITH_ACCEPTED_RISKS** |

---

## Backend readiness (current repository state)

| Gate | Typical dev/CI state | Production sign-off requirement |
|------|----------------------|----------------------------------|
| Backend RC readiness | `PASS` or `PASS_WITH_ENVIRONMENT_SKIPS` | `PASS` or documented skips on RC SHA |
| Backend Docker CI | **PASS** (implementation complete) | **PASS** on RC SHA (not local skip) |
| PP bundle | **NO_GO** — live evidence not attached | **GO** or **GO_WITH_ACCEPTED_RISKS** |

### Backend blockers (explicit)

| Blocker | Effect on backend sign-off | Effect on platform |
|---------|---------------------------|-------------------|
| **Staging secrets missing** (GitHub repo / environment) | **NO_GO** | **NO_GO** |
| **PP-1 live SCIM evidence missing** | **NO_GO** | **NO_GO** |
| **PP-2 live break-glass drill evidence missing** | **NO_GO** | **NO_GO** |
| RC readiness `FAIL` | **NO_GO** | **NO_GO** |
| Docker CI `FAIL` on CI runner | **NO_GO** | **NO_GO** |
| Secret scan / privacy violation in artifacts | **NO_GO** | **NO_GO** |

### PP-3 retention

**Default for this RC line:** **PP-3 `not_required`** when production CR scope does **not** include dedicated retention datasource (`pp3_required=false` in bundle). If scope requires PP-3, missing or failed PP-3 → backend **NO_GO** → platform **NO_GO**.

See [backend-preprod-evidence-bundle.md](backend-preprod-evidence-bundle.md) and [phase-137-summary.md](phases/phase-137-summary.md).

---

## Frontend readiness (current repository state)

| Gate | Typical state | Production sign-off requirement |
|------|---------------|-----------------------------------|
| Frontend RC gate | **PASS** (vitest, tsc, Playwright smoke, build) | **PASS** on RC SHA (`main` / dispatch with Playwright) |
| Visual QA checklist | **INCOMPLETE** (default unchecked) | **Complete** for frontend **GO** |

### Frontend blockers (explicit)

| Blocker | Effect on frontend sign-off | Effect on platform |
|---------|----------------------------|-------------------|
| **Visual QA incomplete** | **NO_GO** | **NO_GO** |
| RC gate `FAIL` | **NO_GO** | **NO_GO** |
| Playwright fail on RC SHA (CI) | **NO_GO** | **NO_GO** |
| TypeScript / Vitest / build fail | **NO_GO** | **NO_GO** |
| Secret/token visible in UI or artifacts | **NO_GO** | **NO_GO** |
| Major route broken (visual QA) | **NO_GO** | **NO_GO** |
| RC `PASS` but visual QA not done | **Not GO** | **NO_GO** |

PR-only RC run with Playwright skipped does **not** justify platform **GO** without a full gate on the release SHA.

See [phase-147-summary.md](phases/phase-147-summary.md).

---

## Domain sign-off mapping

| Domain sign-off | Source rules | Contributes to platform |
|---------------|--------------|-------------------------|
| Backend | [backend-release-candidate-signoff.md](backend-release-candidate-signoff.md#final-decision-rules) | Required |
| Frontend | [frontend-release-candidate-signoff.md](frontend-release-candidate-signoff.md#final-decision-rules) | Required |

Platform **does not** upgrade a domain **NO_GO** to **GO**.

---

## Platform final decision rules

Apply in order; first match wins unless noted.

| # | Condition | Platform final decision |
|---|-----------|-------------------------|
| P1 | Secret/token leak in any artifact, UI evidence, or sign-off file | **NO_GO** |
| P2 | Backend RC `FAIL` or Docker CI `FAIL` (CI runner) or Frontend RC `FAIL` | **NO_GO** |
| P3 | Backend PP bundle `NO_GO` or PP-1/PP-2 missing/fail | **NO_GO** |
| P4 | PP-3 required and missing/fail | **NO_GO** |
| P5 | Frontend visual QA incomplete | **NO_GO** |
| P6 | Open platform blocker in go/no-go checklist | **NO_GO** |
| P7 | Backend sign-off **GO** + Frontend sign-off **GO** + no open blocker | **GO** |
| P8 | Either domain **GO_WITH_ACCEPTED_RISKS** + documented approvals per risk | **GO_WITH_ACCEPTED_RISKS** |
| P9 | Backend **NO_GO** or Frontend **NO_GO** | **NO_GO** |

**Notes:**

- Do **not** record platform **GO** while backend PP live evidence is missing ([phase-137-summary.md](phases/phase-137-summary.md)).
- Do **not** record platform **GO** while frontend visual QA is incomplete ([frontend-release-visual-qa-checklist.md](frontend-release-visual-qa-checklist.md)).
- `PASS_WITH_ENVIRONMENT_SKIPS` on backend RC is acceptable only when skips are documented; production platform **GO** should prefer full **PASS** on CI for the RC SHA.
- Platform **GO** does not auto-enable flags — execute [flag flip plan](backend-production-flag-flip-plan.md) via approved CR only.

---

## Production flag wave (reference only)

| Field | Value |
|-------|--------|
| First wave selected | TBD — from [backend-production-flag-flip-plan.md](backend-production-flag-flip-plan.md) |
| Flags in wave | TBD (no enables in this document) |
| Rollback owner | TBD — [backend-production-rollback-matrix.md](backend-production-rollback-matrix.md) |

---

## Approver matrix

| Platform decision | Required |
|-------------------|----------|
| **GO** | Platform/release manager + security + backend owner + frontend owner + QA (visual QA complete) |
| **GO_WITH_ACCEPTED_RISKS** | Above + risk owner sign-off per accepted risk ID |
| **NO_GO** | No production flag CRs; may merge code per branch policy |

---

## Current repository platform decision (Faz 149)

| Field | Value |
|-------|--------|
| **Platform final decision** | **NO_GO** |
| Backend domain | **NO_GO** — staging secrets missing; PP-1 / PP-2 live evidence missing; bundle `NO_GO` |
| Frontend domain | **GO_WITH_ACCEPTED_RISKS** — RC gate **PASS**; visual QA **complete** (Faz 149); accepted risks AR-FE-149-* |
| Production flags | **Not enabled** |

Platform remains **NO_GO** until backend PP evidence is **GO** and platform go/no-go checklist is signed. Frontend domain is no longer blocked by visual QA.

Update this table only after backend artifacts and platform checklist are attached on the RC SHA.

---

## After platform sign-off

| Decision | Next step |
|----------|-----------|
| **GO** / **GO_WITH_ACCEPTED_RISKS** | Open production CR; execute **one** flag wave; attach rollback owner |
| **NO_GO** | Resolve blockers; re-run gates and PP/visual QA; do not enable production flags |
