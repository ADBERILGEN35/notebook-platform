# Backend release candidate sign-off package (Faz 131)

Single **signable** document set for a backend release candidate (RC): ties together RC SHA, automated gates, pre-prod evidence, flag-flip plan references, risks, and a explicit **GO / NO_GO / GO_WITH_ACCEPTED_RISKS** decision.

**Does not enable production flags.** Sign-off authorizes proceeding to **approved change requests** per [backend-production-flag-flip-plan.md](backend-production-flag-flip-plan.md).

Related:

| Document | Role |
|----------|------|
| [platform-release-candidate-signoff.md](platform-release-candidate-signoff.md) | **Full platform** GO/NO_GO (backend + frontend) — Faz 148 |
| [platform-release-go-no-go-checklist.md](platform-release-go-no-go-checklist.md) | Platform artifact + ops checklist |
| [backend-release-freeze-checklist.md](backend-release-freeze-checklist.md) | Pre-deploy freeze gates |
| [backend-staging-pp-evidence-execution-plan.md](backend-staging-pp-evidence-execution-plan.md) | How to obtain PP artifacts |
| [backend-preprod-evidence-bundle.md](backend-preprod-evidence-bundle.md) | Bundle schema |
| [backend-production-approval-gate.md](backend-production-approval-gate.md) | PP interpretation |
| [backend-production-readiness-review.md](backend-production-readiness-review.md) | Blockers and high risks |
| [security-threat-model.md](security-threat-model.md) | Threat model for flag families |

---

## When to use

1. RC branch/tag is frozen (see freeze checklist).
2. **Backend RC Readiness** and **Backend Docker CI** workflows have run on the RC SHA.
3. Staging PP workflows have produced sanitized evidence (or scope waives PP-3).
4. **Backend Pre-prod Evidence Bundle** has been built from live artifacts (not fixture-only).

Generate a blank copy:

```bash
bash scripts/security/generate-backend-rc-signoff-template.sh \
  --rc-id rc-2026-05-17 \
  --output backend-rc-signoff-rc-2026-05-17.md
```

Fill verdicts from attached JSON/Markdown summaries — **never paste secrets into the sign-off file.**

---

## Required artifacts

| # | Artifact | Workflow / script | Used for |
|---|----------|-------------------|----------|
| 1 | `backend-rc-readiness-summary.md` | Backend RC Readiness | RC gate verdict |
| 2 | `backend-rc-readiness-results.json` | same | Audit automation |
| 3 | `backend-docker-ci-summary.md` | Backend Docker CI | Docker/Testcontainers + full Gradle |
| 4 | `backend-docker-ci-results.json` | same | Audit automation |
| 5 | `backend-preprod-evidence-summary.md` | Backend Pre-prod Evidence Bundle | PP aggregate verdict |
| 6 | `backend-preprod-evidence-bundle.json` | same | PP-1..PP-3 status slices |
| 7 | `scim-delta-sandbox-evidence.json` | SCIM Delta Readiness | PP-1 |
| 8 | `break-glass-revocation-evidence.json` | Break-glass Revocation Readiness | PP-2 |
| 9 | `retention-staging-smoke-results.json` | Retention Readiness | PP-3 (if required) |
| 10 | Signed [freeze checklist](backend-release-freeze-checklist.md) | Manual | Process gate |

---

## Sign-off sections (package contents)

The sign-off file (template or completed) must include:

| Section | Content |
|---------|---------|
| Release candidate ID | e.g. `rc-2026-05-17` |
| Git SHA / image tags | Staging + prod promotion tags |
| RC Readiness verdict | `PASS`, `PASS_WITH_ENVIRONMENT_SKIPS`, or `FAIL` |
| Docker CI verdict | `PASS`, `ENVIRONMENT_SKIPPED` (local only), or `FAIL` |
| PP evidence bundle verdict | `GO`, `GO_WITH_ACCEPTED_RISKS`, or `NO_GO` |
| PP-1 SCIM evidence status | `pass` / `fail` / `missing` (from bundle slice) |
| PP-2 Break-glass evidence status | `pass` / `fail` / `missing` |
| PP-3 Retention status | `pass` / `fail` / `missing` / `not_required` |
| Flag wave plan reference | Link to flip plan; list families in scope |
| Rollback matrix reference | Link; rollback owner named |
| Open blockers | Table with open/closed |
| High risks | From readiness review + RC-specific |
| Accepted risks | From bundle `acceptedRisks` or CR |
| Final decision | `GO` / `NO_GO` / `GO_WITH_ACCEPTED_RISKS` |
| Approvers | Platform, security, optional identity/DBA |

---

## Final decision rules

Apply in order; first failing rule yields **NO_GO** unless noted.

| # | Condition | Final decision |
|---|-----------|----------------|
| D1 | Privacy violation in any attached artifact or bundle output | **NO_GO** |
| D2 | RC readiness `FAIL` | **NO_GO** |
| D3 | Docker CI `FAIL` on CI runner (not local skip) | **NO_GO** |
| D4 | PP bundle `NO_GO` | **NO_GO** |
| D5 | PP-1 or PP-2 `missing` or `fail` in bundle | **NO_GO** |
| D6 | PP-3 required and `missing` or `fail` | **NO_GO** |
| D7 | PP bundle `GO_WITH_ACCEPTED_RISKS` | **GO_WITH_ACCEPTED_RISKS** (security sign-off on listed risks) |
| D8 | RC `PASS` or `PASS_WITH_ENVIRONMENT_SKIPS` + Docker CI `PASS` + bundle `GO` | **GO** |

**Notes:**

- `PASS_WITH_ENVIRONMENT_SKIPS` is acceptable for RC sign-off only when skips are documented (e.g. helm not on developer laptop); CI `main` / release workflow should aim for full `PASS` where possible.
- `ENVIRONMENT_SKIPPED` for Docker CI is **not** sufficient for production sign-off on CI — use a green **Backend Docker CI** run on the RC SHA.
- Fixture-only bundle with no live staging inputs must be recorded as **NO_GO** for production flag flip.

---

## Mapping bundle JSON to PP status

From `backend-preprod-evidence-bundle.json` (see [backend-preprod-evidence-bundle.md](backend-preprod-evidence-bundle.md)):

| Field | Sign-off field |
|-------|----------------|
| `finalRecommendation` | PP bundle verdict / contributes to final decision |
| `gates.pp1.status` | PP-1 SCIM status |
| `gates.pp2.status` | PP-2 break-glass status |
| `gates.pp3.status` | PP-3 retention / `not_required` |
| `acceptedRisks` | Accepted risks table |
| `highRiskCount` | Triggers `GO_WITH_ACCEPTED_RISKS` when combined with G8 in approval gate |
| `privacyViolation` | If true → **NO_GO** |

---

## Approver matrix

| Final decision | Required approvers |
|----------------|-------------------|
| **GO** | Platform/release manager + security |
| **GO_WITH_ACCEPTED_RISKS** | Above + documented risk owner sign-off per risk ID |
| **NO_GO** | No production flag CRs; may still ship artifacts if merge policy allows |

Optional specialists when flag families are in scope:

| Family | Additional approver |
|--------|-------------------|
| SCIM delta | Identity / integration owner |
| Retention datasource | DBA + security |
| Break-glass | Security + on-call lead |

---

## After sign-off

| Decision | Next step |
|----------|-----------|
| **GO** / **GO_WITH_ACCEPTED_RISKS** | Open CR from [backend-production-change-request-template.md](backend-production-change-request-template.md); execute **one wave** from [backend-production-flag-flip-plan.md](backend-production-flag-flip-plan.md) |
| **NO_GO** | Resolve blockers; re-run staging PP + bundle; do not merge prod GitOps enables |

---

## Updating sign-off after live PP bundle (Faz 132)

After [backend-live-staging-pp-evidence-run-checklist.md](backend-live-staging-pp-evidence-run-checklist.md):

1. Build bundle via `run-backend-live-staging-pp-evidence.sh --all` (or `--build-bundle` after download) — outputs under `live-pp-evidence-out/pp-input/` (Faz 135).
2. Read `backend-preprod-evidence-bundle.json` → `finalRecommendation` and `gates.pp1`..`pp3`.
3. Set sign-off **PP bundle verdict** and PP rows to match (do not override bundle with manual GO).
4. Set **final decision** only when RC + Docker CI artifacts for the same SHA also pass ([decision rules](#final-decision-rules)).
5. If bundle is `NO_GO`, sign-off **final decision** remains **NO_GO**.

## Current repository state (template phase)

Live staging PP evidence and a production **GO** bundle were **not** produced in Faz 131–137 without GitHub repository staging secrets. Default generated template sets **final decision: NO_GO** until operators attach real artifacts.

When bundle **GO** (Faz 137+): set PP verdict from `live-pp-evidence-out/backend-preprod-evidence-bundle.json`, attach `backend-preprod-evidence-summary.md`, `backend-rc-readiness-summary.md`, and `backend-docker-ci-summary.md` for the same SHA — see [phase-137-summary.md](phases/phase-137-summary.md) § RC sign-off when bundle GO.

## Platform sign-off (Faz 148)

Backend domain sign-off is **one input** to [platform-release-candidate-signoff.md](platform-release-candidate-signoff.md). Platform **GO** requires backend **GO** (or **GO_WITH_ACCEPTED_RISKS** with approvals) **and** frontend **GO** (visual QA complete). Staging secrets missing or PP-1/PP-2 live evidence missing → backend **NO_GO** → platform **NO_GO**. PP-3 default **not_required** unless production CR scope requires dedicated retention datasource.
