# Platform release GO / NO-GO checklist (Faz 148)

Use with [platform-release-candidate-signoff.md](platform-release-candidate-signoff.md) before recording a platform final decision.

**All items default unchecked.** Checking a box means a human verified the item for the RC SHA — not automated **GO**.

> **Default platform decision: NO_GO** until this checklist is complete and domain sign-offs allow **GO**.

**RC ID:** `PLATFORM-RC-PLACEHOLDER` · **Git SHA:** `SHA-PLACEHOLDER` · **Release manager:** `NAME-PLACEHOLDER` · **Date (UTC):** `DATE-PLACEHOLDER`

---

## A. Artifact attachment

| # | Item | Attached | SHA matches RC |
|---|------|----------|----------------|
| A1 | Backend RC gate — `backend-rc-readiness-summary.md` (+ JSON) | [ ] | [ ] |
| A2 | Backend Docker CI — `backend-docker-ci-summary.md` (+ JSON) | [ ] | [ ] |
| A3 | Backend PP bundle — `backend-preprod-evidence-summary.md` (+ bundle JSON) | [ ] | [ ] |
| A4 | Frontend RC gate — `frontend-rc-readiness-summary.md` (+ JSON) | [ ] | [ ] |
| A5 | Frontend visual QA — completed [frontend-release-visual-qa-checklist.md](frontend-release-visual-qa-checklist.md) | [ ] | [ ] |

---

## B. Automated gate verdicts (from artifacts — verify, do not assume)

| # | Gate | Expected for platform GO | Verified |
|---|------|--------------------------|----------|
| B1 | Backend RC readiness | `PASS` (or documented `PASS_WITH_ENVIRONMENT_SKIPS`) | [ ] |
| B2 | Backend Docker CI | `PASS` on CI runner | [ ] |
| B3 | Backend PP bundle | `GO` (not fixture-only / not `NO_GO`) | [ ] |
| B4 | Frontend RC gate | `PASS` (full Playwright on release SHA) | [ ] |
| B5 | No secret scan failures | `check-no-secrets` + gate privacy clean | [ ] |

---

## C. Backend PP evidence (live staging)

| # | Item | Status | Notes |
|---|------|--------|-------|
| C1 | Staging secrets provisioned per [backend-staging-pp-secrets-governance.md](backend-staging-pp-secrets-governance.md) | [ ] | Missing → backend **NO_GO** |
| C2 | PP-1 SCIM delta sandbox evidence attached | [ ] | Required |
| C3 | PP-2 break-glass revocation drill evidence attached | [ ] | Required |
| C4 | PP-3 retention smoke | [ ] N/A **not_required** / [ ] pass / [ ] fail | Default **not_required** unless prod CR requires datasource |

---

## D. Frontend visual QA

| # | Item | Verified |
|---|------|----------|
| D1 | Visual QA checklist fully completed (or waivers documented in sign-off) | [x] Faz 149 |
| D2 | RC gate **PASS** alone is **not** sufficient — D1 required for frontend **GO** | [x] acknowledged |
| D3 | No token/secret visible in UI during QA session | [ ] |

---

## E. Production change control

| # | Item | Verified |
|---|------|----------|
| E1 | No production feature flag changed outside approved CR | [ ] |
| E2 | [backend-release-freeze-checklist.md](backend-release-freeze-checklist.md) satisfied for RC line | [ ] |
| E3 | First production wave selected from [backend-production-flag-flip-plan.md](backend-production-flag-flip-plan.md) | [ ] |
| E4 | Rollback plan reviewed — [backend-production-rollback-matrix.md](backend-production-rollback-matrix.md) | [ ] |

---

## F. Operations ownership

| Role | Name / rotation | Assigned |
|------|-----------------|----------|
| Rollback owner | | [ ] |
| Monitoring owner | | [ ] |
| On-call / incident | | [ ] |

---

## G. Domain sign-off linkage

| Domain | Final decision (from domain sign-off) | Matches artifacts |
|--------|--------------------------------------|-------------------|
| Backend | GO / GO_WITH_ACCEPTED_RISKS / NO_GO | [ ] |
| Frontend | GO / GO_WITH_ACCEPTED_RISKS / NO_GO | [x] **GO_WITH_ACCEPTED_RISKS** (Faz 149) |

---

## H. Platform decision (fill in sign-off doc, not here)

| Field | Value |
|-------|--------|
| Open blockers count | — |
| **Platform final decision** | **NO_GO** (default) |
| Linked sign-off | [platform-release-candidate-signoff.md](platform-release-candidate-signoff.md) |

**Rules reminder:**

- Backend **GO** + Frontend **GO** + no open blocker → Platform **GO**
- Either domain **GO_WITH_ACCEPTED_RISKS** + explicit risk approval → Platform **GO_WITH_ACCEPTED_RISKS**
- PP evidence missing, visual QA incomplete, gate fail, or secret leak → Platform **NO_GO**

---

**Release manager signature:** _________________________ **Date (UTC):** __________
