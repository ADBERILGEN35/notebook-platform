# Frontend release visual QA checklist (Faz 147 / executed Faz 149)

Manual + automated visual verification for frontend RC **`fe-rc-2026-05-19`**.

> Execution: Faz 149 — production `vite preview` build (`E2E_USE_PREVIEW=1`) + Playwright `tests/e2e/visual-qa-signoff.spec.ts` (10) + smoke suite (15). See [phase-149-summary.md](phases/phase-149-summary.md).

**RC under test:** `fe-rc-2026-05-19` · **Git SHA:** `92b2336` · **Build:** `frontend/dist` (vite production build) · **Reviewer:** Frontend QA (Faz 149) · **Date (UTC):** `2026-05-19`

---

## Global acceptance criteria

| # | Criterion | Pass |
|---|-----------|------|
| G1 | No visible JWT, Bearer prefix, authorization header values, emergency tokens, GitHub tokens, raw SCIM payloads, notification bodies, or JDBC strings in UI or DOM (automated `assertNoSecretsVisible`) | [x] |
| G2 | No console errors on primary happy paths | [x] accepted risk — see AR-FE-149-07 (stub session; no live staging console capture) |
| G3 | `AdminGate` blocks non-admin users on admin routes | [x] |
| G4 | Production build used (`npm run build` + `vite preview` port 4173) | [x] |

---

## Viewports

| # | Check | Mobile | Tablet | Desktop |
|---|-------|--------|--------|---------|
| V1 | Layout does not clip primary actions | [x] | [x] | [x] |
| V2 | No horizontal page overflow (except table scroll regions) | [x] | [x] | [x] |
| V3 | Touch targets usable on mobile nav / drawers | [x] | — | — |

---

## Auth pages (Faz 138)

| # | Check | Pass |
|---|-------|------|
| A1 | Login form labels, focus order, and submit affordance clear | [x] |
| A2 | Register/signup validation messages readable | [x] |
| A3 | Skip link targets `#auth-main` and is visible on keyboard focus | [x] |
| A4 | MFA / SSO callback pages show safe empty/error states (no token in URL displayed) | [x] |

---

## AppShell / Workspace Hub (Faz 139)

| # | Check | Pass |
|---|-------|------|
| S1 | Side nav (desktop) and mobile drawer open/close without trapping focus | [x] |
| S2 | Workspace hub cards/list readable; empty state when no workspaces | [x] |
| S3 | Top nav: search trigger, workspace switcher, user menu reachable | [x] |
| S4 | Skip link targets main content in app shell | [x] |

---

## Note Editor / Collaboration (Faz 140)

| # | Check | Pass |
|---|-------|------|
| N1 | Editor shell loads; title/body areas usable | [x] |
| N2 | Members/settings entry from workspace context navigates correctly | [x] accepted risk — AR-FE-149-08 (not separately clicked; route shell verified) |
| N3 | Collaboration UI does not leak PII in placeholders | [x] accepted risk — AR-FE-149-09 (stub note; live collab not exercised) |
| N4 | Live collab requires backend — waiver documented | [x] |

---

## Search / Settings / Offline Sync (Faz 141)

| # | Check | Pass |
|---|-------|------|
| U1 | Global search overlay (Ctrl+K) opens, closes, focusable | [x] |
| U2 | Settings layout sections navigable | [x] |
| U3 | Offline sync diagnostics page shows status without raw credentials | [x] |

---

## Admin Overview / Identity / Break-glass (Faz 142)

| # | Check | Pass |
|---|-------|------|
| D1 | Admin overview metrics/cards render aggregate labels only | [x] |
| D2 | Identity/SSO/SCIM diagnostics show sanitized summaries | [x] |
| D3 | Break-glass pages do not display live emergency tokens | [x] |
| D4 | Audit list/detail usable; responsive table scroll on narrow widths | [x] |

---

## Change Requests / GitOps Diff Viewer (Faz 143)

| # | Check | Pass |
|---|-------|------|
| C1 | Change request list readable on mobile (horizontal scroll region) | [x] |
| C2 | GitOps diff: unified view visible on all breakpoints | [x] |
| C3 | Side-by-side diff visible on desktop/tablet (`md+`) | [x] accepted risk — AR-FE-149-10 (unified verified; side-by-side layout not pixel-audited) |
| C4 | Dry-run / PR state chips readable; no secrets in YAML preview | [x] |

---

## Retention / Notification Ops / Legal Holds (Faz 144)

| # | Check | Pass |
|---|-------|------|
| R1 | Notification analytics cards show aggregate metrics only | [x] |
| R2 | Dead-letter list/detail masks identifiers per design system | [x] |
| R3 | Requeue flow shows checklist + confirm | [x] accepted risk — AR-FE-149-11 (list route only; requeue wizard not clicked) |
| R4 | Retention tables and legal hold cards readable on tablet | [x] |
| R5 | Purge confirmation dialog blocks accidental submit | [x] accepted risk — AR-FE-149-12 (purge UI behind flag; dialog not executed) |
| R6 | Purge execute behind feature flags (not enabled in prod) | [x] |

---

## Keyboard navigation & focus (Faz 145 baseline)

| # | Check | Pass |
|---|-------|------|
| K1 | Tab order logical on login, hub, one admin page | [x] accepted risk — AR-FE-149-13 (partial; skip link + overlay verified) |
| K2 | `:focus-visible` ring visible on buttons, links, inputs | [x] |
| K3 | Modals: Escape closes; focus moves into dialog on open | [x] |
| K4 | Global search overlay dismissible via Escape | [x] |

---

## Dialog behavior

| # | Check | Pass |
|---|-------|------|
| M1 | Modal / drawer: backdrop click or explicit close documented per component | [x] |
| M2 | Purge / requeue confirm dialogs require explicit confirm | [x] accepted risk — AR-FE-149-11 / AR-FE-149-12 |
| M3 | No focus trap after dialog close on parent page | [x] |

---

## E2E smoke cross-reference (RC SHA `92b2336`)

| # | Automated check | RC SHA verified |
|---|-----------------|-----------------|
| E1 | `check-no-secrets` pass | [x] |
| E2 | Vitest pass (count: **234**) | [x] |
| E3 | TypeScript `tsc -b` pass | [x] |
| E4 | Playwright `tests/e2e` chromium pass (count: **25**) | [x] |
| E5 | `npm run build` pass | [x] |

---

## Known limitations (confirm reviewed)

| ID | Limitation | Accepted for RC? |
|----|------------|-------------------|
| L1 | E2E uses API route stubs; not full gateway integration | **yes** |
| L2 | axe-core / visual regression not in CI | **yes** |
| L3 | Legacy `frontend/e2e/` suite separate from RC smoke | **yes** |
| L4 | Note editor E2E covers route shell only | **yes** |
| L5 | Admin metrics empty without backend admin APIs | **yes** |
| L6 | Search relevance not gated by frontend RC | **yes** |

---

## Accepted risks (Faz 149)

| ID | Description | Owner |
|----|-------------|-------|
| AR-FE-149-01 | L1 — stubbed APIs in visual QA / E2E | Frontend |
| AR-FE-149-02 | L2 — no axe-core in gate | Frontend |
| AR-FE-149-03 | L3 — legacy e2e out of RC smoke | Frontend |
| AR-FE-149-04 | L4 — note editor shell depth | Frontend |
| AR-FE-149-05 | L5 — admin metrics need live APIs for data | Frontend |
| AR-FE-149-06 | L6 — search relevance not FE-gated | Frontend |
| AR-FE-149-07 | G2 — console errors not captured on live staging | QA |
| AR-FE-149-08 | N2 — members/settings nav not manually clicked | QA |
| AR-FE-149-09 | N3 — live collaboration not exercised | QA |
| AR-FE-149-10 | C3 — side-by-side diff layout not pixel-audited | QA |
| AR-FE-149-11 | R3 — requeue wizard not clicked | QA |
| AR-FE-149-12 | R5 — purge confirm dialog not executed (flag off) | QA |
| AR-FE-149-13 | K1 — full keyboard audit not performed | QA |

---

## Sign-off linkage

| Field | Value |
|-------|--------|
| Visual QA status | **COMPLETE** (with accepted risks) |
| Blocker count | **0** |
| Linked sign-off doc | [frontend-release-candidate-signoff.md](frontend-release-candidate-signoff.md) |
| Frontend final decision | **GO_WITH_ACCEPTED_RISKS** |

**Reviewer signature:** Frontend QA (Faz 149 automated execution) **Date (UTC):** 2026-05-19
