# Frontend release candidate sign-off package (Faz 147)

Single **signable** document set for a **frontend** release candidate (RC): ties together RC SHA, **Frontend RC Readiness** gate artifacts, route inventory, E2E smoke scope, responsive/accessibility acceptance, visual QA completion, known limitations, and an explicit **GO / NO_GO / GO_WITH_ACCEPTED_RISKS** decision.

**Does not enable production feature flags.** Sign-off authorizes frontend promotion / release ticket closure when paired with backend sign-off per org process.

Related:

| Document | Role |
|----------|------|
| [platform-release-candidate-signoff.md](platform-release-candidate-signoff.md) | **Full platform** GO/NO_GO (backend + frontend) — Faz 148 |
| [platform-release-go-no-go-checklist.md](platform-release-go-no-go-checklist.md) | Platform artifact + ops checklist |
| [frontend-release-visual-qa-checklist.md](frontend-release-visual-qa-checklist.md) | Manual visual QA (unchecked by default) |
| [phases/phase-146-summary.md](phases/phase-146-summary.md) | RC gate implementation |
| [frontend-implementation-plan.md](frontend-implementation-plan.md) | Route and domain map |
| [frontend-design-system.md](frontend-design-system.md) | A11y / responsive patterns |
| [backend-release-candidate-signoff.md](backend-release-candidate-signoff.md) | Backend RC (separate gate) |

---

## When to use

1. RC branch/tag is frozen for frontend (no unapproved UI feature work on the RC line).
2. **Frontend RC Readiness** workflow has run on the RC SHA (`main` or `workflow_dispatch` with Playwright installed).
3. Visual QA checklist completed by a human reviewer (all applicable rows checked or waivers documented).
4. Sanitized artifacts attached to the release ticket — **never paste secrets into the sign-off file.**

Generate a blank copy:

```bash
bash scripts/security/generate-frontend-rc-signoff-template.sh \
  --rc-id fe-rc-2026-05-19 \
  --output frontend-rc-signoff-fe-rc-2026-05-19.md
```

Fill verdicts from `frontend-rc-readiness-out/` and visual QA checklist — use counts/verdicts only, not raw API bodies.

---

## Required artifacts

| # | Artifact | Source | Used for |
|---|----------|--------|----------|
| 1 | `frontend-rc-readiness-summary.md` | [frontend-rc-readiness.yml](../.github/workflows/frontend-rc-readiness.yml) / `ci-frontend-rc-readiness.sh` | Gate verdict + check table |
| 2 | `frontend-rc-readiness-results.json` | same | Automation / audit (`frontend-rc-readiness-v1`) |
| 3 | Completed [visual QA checklist](frontend-release-visual-qa-checklist.md) | Manual | UX / responsive / a11y sign-off |
| 4 | Production build reference | CI log or `frontend/dist/` manifest (hash/size only) | Build reproducibility |
| 5 | Optional: Playwright HTML report (sanitized) | CI artifact | Smoke scope evidence |

**Not required for frontend sign-off:** backend PP bundle, JDBC credentials, SCIM raw payloads, notification message bodies, or live tokens.

---

## Sign-off sections (package contents)

The sign-off file (template or completed) must include:

| Section | Content |
|---------|---------|
| Release candidate ID | e.g. `fe-rc-2026-05-19` |
| Git SHA / frontend build artifact | SHA + dist artifact id or CI job URL (no secrets) |
| Frontend RC gate verdict | `PASS`, `PASS_WITH_ENVIRONMENT_SKIPS`, or `FAIL` |
| Vitest result | pass/fail + test count |
| TypeScript result | pass/fail |
| Playwright smoke result | pass/fail/skipped + spec count |
| Production build result | pass/fail |
| Route inventory summary | Counts by domain from RC JSON `routeInventory` |
| User Domain status | Auth, app shell, workspace, notes, search, settings, offline sync |
| Admin Domain status | Overview, identity, break-glass, change requests, GitOps diff, retention, notification ops |
| Responsive status | Mobile / tablet / desktop acceptance |
| Accessibility baseline status | Skip link, focus, modals, tables (Faz 145 baseline) |
| Known limitations | From checklist + phase docs (E2E stubs, axe absent, etc.) |
| Accepted risks | Documented waivers with owner |
| Final decision | `GO` / `NO_GO` / `GO_WITH_ACCEPTED_RISKS` |
| Approvers | Frontend lead, QA, optional accessibility reviewer |

---

## Mapping RC JSON to sign-off fields

From `frontend-rc-readiness-results.json`:

| Field | Sign-off field |
|-------|----------------|
| `verdict` | Frontend RC gate verdict |
| `checks[]` where `id=check-no-secrets` | Secret scan |
| `checks[]` where `id=vitest-unit` | Vitest |
| `checks[]` where `id=typescript-build` | TypeScript |
| `checks[]` where `id=playwright-smoke` | Playwright |
| `checks[]` where `id=frontend-production-build` | Production build |
| `routeInventory` | Route inventory summary |
| `environmentSkips` | Document if `PASS_WITH_ENVIRONMENT_SKIPS` |
| `smokeSummary` | E2E scope note |

---

## Domain status reference (foundation phases)

Use **ready / partial / blocked** per domain after visual QA. Automated gate alone is not sufficient for **GO**.

### User Domain (Faz 138–141)

| Area | Routes (representative) | Automated smoke | Backend-dependent limits |
|------|-------------------------|-----------------|---------------------------|
| Auth | `/login`, `/register`, `/signup`, `/mfa`, `/sso/callback` | `auth.spec.ts` | Live SSO/IdP not exercised in smoke |
| App shell / hub | `/app`, `/app/workspaces` | `app-shell.spec.ts` | Workspace list empty without API |
| Note editor / collab | `/app/notes/:noteId`, workspace note paths | Route shell only in E2E | Real-time collab needs gateway |
| Search / settings / offline | `/app/search`, `/app/settings`, `/app/sync` | `search.spec.ts` | Search index quality not gated |

### Admin Domain (Faz 142–144)

| Area | Routes (representative) | Automated smoke | Backend-dependent limits |
|------|-------------------------|-----------------|---------------------------|
| Admin overview / setup | `/app/overview`, `/app/setup`, `/app/admin` | `admin.spec.ts` | Metrics require admin APIs |
| Identity / security | `/app/identity/*`, `/app/security/*` | Stubbed in E2E | SCIM/SSO live config not in FE gate |
| Change requests / GitOps | `/app/change-requests/*` | `change-requests.spec.ts` | Dry-run uses sessionStorage seed |
| Retention / notification ops | `/app/notifications/*`, `/app/retention/*` | `retention-notifications.spec.ts` | Purge execute behind feature flags (not enabled) |

---

## Final decision rules

Apply in order; first failing rule yields **NO_GO** unless noted.

| # | Condition | Final decision |
|---|-----------|----------------|
| F1 | Secret/token leak in UI, artifact, or sign-off attachment | **NO_GO** |
| F2 | Frontend RC gate `FAIL` | **NO_GO** |
| F3 | Playwright smoke `fail` on RC SHA (CI run, not local skip) | **NO_GO** |
| F4 | TypeScript `fail` | **NO_GO** |
| F5 | Vitest `fail` or production build `fail` | **NO_GO** |
| F6 | Major route broken (404/regression on primary nav) per visual QA | **NO_GO** |
| F7 | RC `PASS` + visual QA completed + no open blocker | **GO** |
| F8 | RC `PASS` or `PASS_WITH_ENVIRONMENT_SKIPS` + visual QA completed + documented accepted visual limitations | **GO_WITH_ACCEPTED_RISKS** |

**Notes:**

- `PASS_WITH_ENVIRONMENT_SKIPS` is acceptable only when skips are **documented** (e.g. PR gate skipped Playwright; production sign-off requires a **full** `main` / `workflow_dispatch` run with Playwright **pass** on the RC SHA).
- PR-only RC runs with `RC_SKIP_PLAYWRIGHT=true` must **not** alone justify **GO** — attach a green full gate on the release SHA.
- Visual QA checklist defaults are **unchecked**; incomplete checklist → **NO_GO**.
- Backend sign-off is **out of scope** for this document; frontend **GO** does not imply backend production readiness.

---

## Approver matrix

| Final decision | Required approvers |
|----------------|-------------------|
| **GO** | Frontend / release owner + QA (visual checklist complete) |
| **GO_WITH_ACCEPTED_RISKS** | Above + documented risk owner per accepted limitation |
| **NO_GO** | No frontend production promotion; fix and re-run gates |

Optional:

| Topic | Additional reviewer |
|-------|---------------------|
| Accessibility | Design / a11y champion when waiving a11y rows |
| Admin surfaces | Platform admin UX owner |

---

## After sign-off

1. Attach sanitized artifacts to the release ticket / CR.
2. Record final decision and approver table in the ticket system of record.
3. Do **not** enable production feature flags from this document.
4. Coordinate with [backend-release-candidate-signoff.md](backend-release-candidate-signoff.md) before end-to-end production rollout.

---

## Updating sign-off after RC gate re-run

1. Re-run `bash scripts/security/ci-frontend-rc-readiness.sh` or **Frontend RC Readiness** on the new SHA.
2. Replace attached `frontend-rc-readiness-summary.md` and JSON.
3. Re-verify visual QA for changed routes only, or full checklist if routing changed.
4. If gate `FAIL`, final decision remains **NO_GO** until green.

## Platform sign-off (Faz 148)

Frontend domain sign-off is **one input** to [platform-release-candidate-signoff.md](platform-release-candidate-signoff.md). **RC gate PASS alone does not yield platform GO** — visual QA checklist must be complete. Visual QA incomplete → frontend **NO_GO** → platform **NO_GO**.

---

## Current frontend sign-off status (Faz 149)

| Field | Value |
|-------|--------|
| Release candidate ID | `fe-rc-2026-05-19` |
| Git SHA | `92b2336` |
| Frontend build | `frontend/dist` (vite production build; preview port 4173 for QA) |
| Frontend RC gate verdict | **PASS** |
| Vitest | **pass** (234) |
| TypeScript | **pass** |
| Playwright smoke + visual QA | **pass** (25 specs on production preview) |
| Production build | **pass** |
| Visual QA checklist | **COMPLETE** — [frontend-release-visual-qa-checklist.md](frontend-release-visual-qa-checklist.md) |
| User domain status | **ready** (with accepted stub/API limits) |
| Admin domain status | **ready** (with accepted stub/API limits) |
| Responsive / a11y baseline | **pass** (mobile/tablet/desktop automated; see accepted risks) |
| Open blockers | **none** |
| **Frontend final decision** | **GO_WITH_ACCEPTED_RISKS** |

### Accepted risks (summary)

Documented in checklist § Accepted risks (AR-FE-149-01 … AR-FE-149-13): stubbed APIs, no axe-core, partial manual depth (requeue/purge flows, live collab, staging console audit).

### Approvers (placeholder)

| Role | Sign-off |
|------|----------|
| Frontend / release owner | [ ] |
| QA | [x] Faz 149 execution recorded |
| Accessibility (waivers) | [ ] |

**Production feature flags:** not enabled by this sign-off.
