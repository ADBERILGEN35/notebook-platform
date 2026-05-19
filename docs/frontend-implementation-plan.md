# Frontend Implementation Plan

Notebook Platform SPA (React 19 + Vite + TypeScript). Fazlar `docs/phases/` altında numaralanır.

## Tamamlanan foundation

| Faz | Kapsam |
|-----|--------|
| 138 | Auth routes + reusable auth components |
| 139 | AppShell + Workspace Hub (`/app`, `/app/workspaces`) |
| 140 | Note editor, collaboration, members, settings, notification center |
| 141 | Global search, discovery, user settings layout, offline sync diagnostics |
| 142 | Admin overview, setup, search shell, identity/SSO/SCIM/RBAC diagnostics, break-glass ops |
| 143 | Change requests + GitOps dry-run/PR states + YAML diff viewer |
| 144 | Notification ops (analytics, dead-letter, requeue) + retention governance + legal holds |
| 145 | E2E smoke, responsive polish, accessibility baseline (no new product features) |
| 146 | Frontend RC CI gate + evidence package (`frontend-rc-readiness`) |
| 147 | Frontend RC sign-off package + visual QA checklist |
| 149 | Frontend visual QA execution + sign-off (GO_WITH_ACCEPTED_RISKS) |

## Dizin yapısı

```
frontend/src/
  app/           router, providers
  features/      domain modules (auth, app-shell, workspaces, …)
  pages/         route-level composition
  shared/        components, hooks, config, types
```

## App shell (Faz 139)

- **Shell:** `features/app-shell/components/AppShell.tsx` — `AppShellPage` wraps `<Outlet />`.
- **Nav:** `SideNav` (desktop), `MobileNavDrawer` + `MobileNav` (mobile).
- **Top:** `TopNav` — search, create note, `WorkspaceSwitcher`, `UserMenu`, `NotificationBell`.
- **Hub:** `pages/WorkspaceHubPage.tsx` — workspaces API; empty create form; populated cards + recent notebooks.

## Routing kuralları

| Path | Guard | Not |
|------|-------|-----|
| `/login` … `/sso/callback` | Public | Auth foundation |
| `/app/*` | `Protected` | Token veya cookie session |
| `/app/admin/*` | `AdminGate` | Feature flag + role |

## API kullanımı

- Workspaces: `features/workspaces/workspace-api.ts`
- Notebooks: `features/notebooks/notebook-api.ts`
- Auth: `features/auth/auth-api.ts` (Faz 138)

Yeni endpoint uydurulmaz; API yanıtı yoksa empty/error state gösterilir.

## Test stratejisi

- Vitest + Testing Library per feature/page.
- Router smoke: `router.auth.test.tsx`, `router.app.test.tsx`.
- No-secrets: JWT/Bearer regex in auth/hub tests; `scripts/check-no-secrets.sh` repo geneli.

## Faz 140 routes

| Route | Page |
|-------|------|
| `/app/workspaces/:id/notes/:noteId` | `NoteEditorPage` (embeds `NotePage`) |
| `/app/workspaces/:id/notes/:noteId/history` | `NoteEditorHistoryPage` |
| `/app/workspaces/:id/members` | `WorkspaceMembersPage` |
| `/app/workspaces/:id/settings` | `WorkspaceSettingsPage` |
| `/app/notifications` | `NotificationCenterPage` |

Legacy: `/app/notes/:noteId` → `NotePage`.

## Faz 141 routes

| Route | Page |
|-------|------|
| `/app/search` | `SearchResultsPage` (`?q=`) |
| `/app/search/discover` | `SearchDiscoveryPage` |
| `/app/settings/*` | `SettingsLayout` + section pages |
| Ctrl+K | `GlobalSearchOverlay` in `AppShellPage` |

## Faz 142 admin routes

| Route | Page |
|-------|------|
| `/app/admin/overview` | `AdminOverviewPage` |
| `/app/admin/setup` | `AdminSetupChecklistPage` |
| `/app/admin/search` | `AdminSearchDiagnosticsPage` |
| `/app/admin/identity` | `AdminIdentityOverviewPage` |
| `/app/admin/identity/sso` | `AdminSsoDiagnosticsPage` |
| `/app/admin/identity/scim` | `AdminScimProvisioningPage` |
| `/app/admin/identity/role-mapping` | `AdminRoleMappingDiagnosticsPage` |
| `/app/admin/security/break-glass` | `AdminBreakGlassOpsPage` |
| `/app/admin/audit` | `AdminAuditPage` (existing) |

`AdminLayout` nav: overview → identity → break-glass; legacy enterprise/RBAC/notifications linkleri korunur.

## Faz 143 change-request routes

| Route | Page |
|-------|------|
| `/app/admin/change-requests` | List + create |
| `/app/admin/change-requests/:id` | Detail + approve/reject |
| `/app/admin/change-requests/:id/gitops` | GitOps handoff + PR state |
| `/app/admin/change-requests/:id/dry-run` | Dry-run preview |
| `/app/admin/change-requests/:id/diff` | YAML diff viewer |

## Faz 144 notification ops & retention routes

| Route | Page |
|-------|------|
| `/app/admin/notifications/analytics` | Delivery health aggregates |
| `/app/admin/notifications/dead-letter` | Queue table |
| `/app/admin/notifications/dead-letter/:eventId` | Sanitized detail |
| `/app/admin/notifications/dead-letter/:eventId/requeue` | Dry-run + confirm workflow |
| `/app/admin/notifications/retention` | Notification retention plan |
| `/app/admin/retention` | Retention hub |
| `/app/admin/retention/platform` | Platform governance |
| `/app/admin/retention/legal-holds` | Platform legal holds |
| `/app/admin/retention/purge-result` | Session-scoped purge summary |

Shared: `features/admin/notifications/*`, `features/admin/retention/*`. Purge execute gated by `NOTIFICATION_RETENTION_PURGE_*` flag.

## Faz 149 Visual QA execution

- Checklist executed: [frontend-release-visual-qa-checklist.md](frontend-release-visual-qa-checklist.md) (`fe-rc-2026-05-19`, SHA `92b2336`)
- Spec: `tests/e2e/visual-qa-signoff.spec.ts`; preview via `E2E_USE_PREVIEW=1`
- Frontend decision: **GO_WITH_ACCEPTED_RISKS**

## Faz 147 Frontend RC sign-off + visual QA

- Sign-off: [frontend-release-candidate-signoff.md](frontend-release-candidate-signoff.md)
- Checklist: [frontend-release-visual-qa-checklist.md](frontend-release-visual-qa-checklist.md)
- Template: `scripts/security/generate-frontend-rc-signoff-template.sh` (default **NO_GO**)
- CI: `scripts/security/ci-generate-frontend-rc-signoff-template.sh`
- Requires Faz 146 artifacts + completed visual QA for **GO**

## Faz 146 Frontend RC gate

- Script: `scripts/security/ci-frontend-rc-readiness.sh`
- Report: `scripts/security/build_frontend_rc_readiness_report.py`
- Workflow: `.github/workflows/frontend-rc-readiness.yml`
- Artifacts: `frontend-rc-readiness-results.json`, `frontend-rc-readiness-summary.md`
- Verdicts: `PASS`, `PASS_WITH_ENVIRONMENT_SKIPS`, `FAIL`
- PR: `RC_SKIP_PLAYWRIGHT=true` (lightweight); `main` / `workflow_dispatch`: Playwright chromium kurulu

```bash
bash scripts/security/ci-frontend-rc-readiness.sh
```

## Faz 145 E2E & quality

- Playwright: `frontend/tests/e2e/*.spec.ts` + legacy `frontend/e2e/*.spec.ts` (`playwright.config.ts` `testMatch` her ikisini kapsar).
- Helpers: `tests/e2e/helpers/stub-smoke-admin.ts`, `no-secrets.ts`.
- A11y: `SkipToMain`, `:focus-visible`, `ResponsiveTableShell`, `Modal` focus/Escape.
- Vitest: `phase-145-a11y.test.tsx`.

## Sonraki UI fazları (öneri)

1. Release ticket şablonuna sign-off + visual QA bağlama; axe optional.
2. Audit page refactor to shared `AuditEventTable` / drawer.
3. Workspace notification policies in settings notifications page.

## Çalıştırma

```bash
cd frontend && npm run dev
cd frontend && npm test
cd frontend && npx tsc -b
```
