# Faz 152C — Frontend Interaction Audit + Broken Actions Inventory

## Scope

Audit every user-visible action surface (buttons, CTAs, links, modal triggers, form submits) in the local frontend and classify each into one of:

- `WORKING`
- `DISABLED_BY_DESIGN`
- `MISSING_BACKEND_CONTRACT`
- `FEATURE_FLAG_DISABLED`
- `PERMISSION_BLOCKED`
- `BUG_FRONTEND`
- `NEEDS_MANUAL_CONFIRMATION`

The full table lives at [`docs/frontend-interaction-audit.md`](../frontend-interaction-audit.md).

## Hard constraints

- No backend API change.
- No new backend endpoint invented.
- No production feature flag flipped.
- No `AdminGate` / `Protected` bypass.
- No JWT / Bearer / Authorization / break-glass token / SCIM raw payload rendered in UI.
- No mock data in production paths.
- Destructive admin actions remain behind their existing flag + permission.

## Routes & surfaces in scope

| Group | Routes |
|-------|--------|
| Auth | `/login`, `/register`, `/forgot-password`, `/mfa`, `/sso/callback` |
| AppShell | every `/app/**` route (SideNav, TopNav, MobileNav, UserMenu) |
| Workspace dashboard | `/app`, `/app/workspaces`, `/app/workspaces/:id` |
| Workspace admin | `/app/workspaces/:id/members`, `/app/workspaces/:id/settings` |
| Note editor | `/app/workspaces/:id/notes/:nid`, `/app/workspaces/:id/notes/:nid/history` |
| Search | `/app/search`, `/app/search/discover`, Ctrl/Cmd+K overlay |
| Settings | `/app/settings`, `/app/settings/security`, `/app/settings/notifications`, `/app/settings/sync` |
| Admin overview | `/app/admin/overview`, `/app/admin/setup` |
| Admin change requests | `/app/admin/change-requests/**` |
| Admin notifications | `/app/admin/notifications/**` |
| Admin retention | `/app/admin/retention/**` |
| Admin identity/security | `/app/admin/identity/**`, `/app/admin/security/break-glass/**`, `/app/admin/rbac` |

## Fix policy

- `BUG_FRONTEND` → fix safely in this phase.
- `MISSING_BACKEND_CONTRACT` → leave disabled or surface explanatory banner ("not yet wired to backend").
- `FEATURE_FLAG_DISABLED` → keep gated, do NOT enable flag; existing UI already explains it.
- `PERMISSION_BLOCKED` → keep guard, surface `<PermissionDenied>` (already in place).
- Wrong route on a link → fix.
- Click handler missing but safe existing route/API exists → wire it.
- Destructive action without a real mutation → never invent one; only dry-run / safe confirmation.

## Planned BUG_FRONTEND fixes

1. **`SearchResultsPage` ignores `?filter=…` query** — `TopNav` Drafts/Shared/Archived links added in Faz 151C set `?filter=drafts|shared|archived`, but `SearchResultsPage` only reads `q`. Users perceive the filter as "broken." Fix: surface an `InlineStatus` banner + helper text explaining the backend filter contract is not yet available, plus a "Clear filter" button. Search results still honor the `q` query (no fake filtering).
2. **Populated dashboard "New notebook" QuickAction** — `onNewNotebook` only calls `navigate('/app/workspaces/:id')`, which is the same page; label is misleading. The canonical create entry point is the sidebar `New Notebook` modal added in Faz 151C. Fix: relabel the QuickAction as "Open workspace" with description pointing users at the sidebar `New Notebook` button.

## Out-of-scope

- New mutations.
- Real PR creation / real requeue / real purge.
- Templates / "View All" experiences without their backing endpoint.

## Test plan

- `frontend/src/features/workspace-dashboard/phase-152C-interactions.test.tsx` — 12 assertions for empty + populated dashboards (DISABLED_BY_DESIGN coverage, BUG_FRONTEND fix coverage, secret-leak smoke).
- `frontend/src/pages/phase-152C-search-filter-banner.test.tsx` — 7 assertions for filter banner behavior (known filters render banner, unknown filters do not, clear filter button, no secret leak).
- Full `npm test` regression.
- `npx tsc -b` + `npm run build` + `bash scripts/check-no-secrets.sh`.

## Acceptance

- All audit rows present in `docs/frontend-interaction-audit.md` with classification and notes.
- Two BUG_FRONTEND fixes shipped and tested.
- No regressions in existing 66 vitest test files.
- No tokens/secrets in any UI output.
