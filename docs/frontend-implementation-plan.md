# Frontend Implementation Plan

Notebook Platform SPA (React 19 + Vite + TypeScript). Fazlar `docs/phases/` altında numaralanır.

## Tamamlanan foundation

| Faz | Kapsam |
|-----|--------|
| 138 | Auth routes + reusable auth components |
| 139 | AppShell + Workspace Hub (`/app`, `/app/workspaces`) |
| 140 | Note editor, collaboration, members, settings, notification center |
| 141 | Global search, discovery, user settings layout, offline sync diagnostics |

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

## Sonraki UI fazları (öneri)

1. Workspace notification policies in settings notifications page.
2. E2E search + settings flows.
3. Profile update API.

## Çalıştırma

```bash
cd frontend && npm run dev
cd frontend && npm test
cd frontend && npx tsc -b
```
