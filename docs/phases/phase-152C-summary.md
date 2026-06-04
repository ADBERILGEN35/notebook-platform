# Faz 152C — Frontend Interaction Audit (summary)

## 1. Goal

Sweep every visible action surface in the local `/app` frontend, classify each into the
audit taxonomy, fix only the safe `BUG_FRONTEND` items, and produce a single source of
truth at `docs/frontend-interaction-audit.md`. No backend change, no flag flip, no
mutation invention.

## 2. Scope covered

| Group | Status |
|-------|--------|
| Auth (login / register / forgot / SSO / MFA) | audited |
| AppShell (SideNav, TopNav, MobileNav, UserMenu) | audited |
| Workspace dashboard (empty + populated) | audited + fixed |
| Workspace members / settings | audited |
| Note editor / collaboration | audited |
| Global search overlay + results + discovery | audited + fixed |
| User settings (profile, security, notifications, sync) | audited |
| Admin overview / setup / audit | audited |
| Admin change requests | audited |
| Admin notifications (analytics, dead-letter, retention, legal holds) | audited |
| Admin retention (platform, legal holds, purge result) | audited |
| Admin identity / SSO / SCIM / role-mapping | audited |
| Admin security (break-glass, rotation, RBAC) | audited |

## 3. Action counts

Total actions audited: **83**

| Class | Count |
|-------|-------|
| `WORKING` | 39 |
| `DISABLED_BY_DESIGN` | 8 |
| `MISSING_BACKEND_CONTRACT` | 3 |
| `FEATURE_FLAG_DISABLED` | 14 |
| `PERMISSION_BLOCKED` | 11 |
| `BUG_FRONTEND` | 2 (both fixed) |
| `NEEDS_MANUAL_CONFIRMATION` | 6 |

Detailed per-row table: [`docs/frontend-interaction-audit.md`](../frontend-interaction-audit.md).

## 4. Frontend bugs fixed

1. **`SearchResultsPage` silently dropped the `?filter=…` query param** — the `TopNav` Drafts /
   Shared / Archived links added in Faz 151C navigated with `?filter=drafts|shared|archived`,
   but the page only read `q`. Users perceived the filter chips as broken.
   - Fix: read & validate `filter` against a `KNOWN_TOPBAR_FILTERS` set, render an
     `InlineStatus` (`tone="warning"`) banner explaining "Backend filter endpoint is not yet
     available", and provide a `Clear filter` button that removes the param.
   - The `q` query still works; **no fake filtering** was applied to results.
   - File: `frontend/src/pages/SearchResultsPage.tsx`.

2. **Populated dashboard "New notebook" QuickAction was misleading** — the handler navigated to
   the same `/app/workspaces/:id` page, while the sidebar `New Notebook` modal (Faz 151C) is the
   canonical create entry point.
   - Fix: relabel the QuickAction as `Open workspace`, update the description to point users
     at the sidebar `New Notebook` button, and add `data-testid="quick-action-open-workspace"`.
   - Behavior remains identical (navigate to active workspace), but the label is honest.
   - Files: `frontend/src/features/workspace-dashboard/components/WorkspaceDashboardPopulated.tsx`,
     `frontend/src/shared/components/QuickActionCard.tsx` (added `data-testid` prop).

## 5. Actions waiting on backend contract

These remain UI-only / disabled / explanatory; no endpoint was invented.

| Surface | Notes |
|---------|-------|
| `TopNav` Drafts / Shared / Archived filter | Banner now explains "Backend filter endpoint is not yet available". |
| `SearchFilterPanel` notebook filter / sort | Local state only; the backend `searchNotes` API does not yet accept filters. |
| Workspace `Archive workspace` (DangerZone) | Hard-disabled with title hint. No API yet. |
| Workspace Settings → Branding (logo upload) | InfoRow `Upload coming soon`. |
| Empty dashboard "Quick Note" / "Invite Member" | Disabled until at least one workspace exists (which is the canonical pre-condition). |

## 6. Actions held closed by feature flag

All of these obey their existing env flag (still `false` in this environment) and were left as-is:

`ENTERPRISE_ADMIN_WRITE_ENABLED`, `ENTERPRISE_ADMIN_APPROVALS_ENABLED`, `ENTERPRISE_GITOPS_PR_ENABLED`,
`GITOPS_RBAC_ROLE_REQUESTS_ENABLED`, `NOTIFICATION_ANALYTICS_UI_ENABLED`, `NOTIFICATION_DEAD_LETTER_UI_ENABLED`,
`NOTIFICATION_RETENTION_UI_ENABLED`, `NOTIFICATION_RETENTION_PURGE_UI_ENABLED`,
`NOTIFICATION_LEGAL_HOLD_UI_ENABLED`, `ADMIN_RBAC_UI_ENABLED`, `ADMIN_RBAC_ROLE_REQUESTS_ENABLED`,
`PLATFORM_RETENTION_GOVERNANCE_ENABLED`, `BREAK_GLASS_REVIEW_UI_ENABLED`,
`BREAK_GLASS_REVOCATION_UI_ENABLED`, `BREAK_GLASS_ROTATION_UI_ENABLED`,
`SCIM_COMPATIBILITY_DIAGNOSTICS_ENABLED`, `SCIM_DELTA_PROVIDER_POC_UI_ENABLED`.

## 7. Actions held closed by permission

Admin destructive surfaces remain behind `hasPlatformPermission(...)` checks
(`PERM_BREAK_GLASS_REVOKE`, `PERM_CHANGE_REQUEST_*`, `PERM_AUDIT_READ`,
`PERM_ENTERPRISE_STATUS_READ`, etc.). All `PermissionDenied` and `AccessDeniedState`
fallbacks were left in place.

## 8. Backend / flag posture

- **No backend API was changed.**
- **No backend endpoint was added.**
- **No production feature flag was flipped.**
- **No new mutation was wired** — both fixed bugs are purely UI clarifications.
- **No token / JWT / Bearer / Authorization / break-glass / SCIM payload** appears in any
  rendered output (regression tests assert this for both dashboards and the search page).

## 9. Test results

| Suite | Files | Tests | Result |
|-------|-------|-------|--------|
| `npm test -- --run phase-152C` (new) | 2 | 19 | ✅ pass |
| `npm test -- --run` (full regression) | 66 | 279 | ✅ pass |
| `npx tsc -b` | — | — | ✅ no errors |
| `npm run build` | — | — | ✅ built (PWA 63 entries) |
| `bash scripts/check-no-secrets.sh` | — | — | ✅ `No obvious committed secrets detected.` |

### New test files

- `frontend/src/features/workspace-dashboard/phase-152C-interactions.test.tsx`
  - Verifies the BUG_FRONTEND fix for the populated dashboard (`quick-action-open-workspace`
    test-id replaces misleading "New notebook").
  - Verifies `DISABLED_BY_DESIGN` state for `Quick Note` / `Invite Member` / `Create New
    Workspace` when no workspace exists.
  - Verifies `Browse Discovery` link → `/app/search/discover`.
  - Asserts no JWT / Bearer / Authorization patterns in markup.
- `frontend/src/pages/phase-152C-search-filter-banner.test.tsx`
  - Verifies banner renders for `drafts` / `shared` / `archived`.
  - Verifies banner is **not** rendered when filter is absent or unknown.
  - Verifies `Clear filter` removes the param and banner.
  - Asserts no token leaks in markup.

### Files touched

- `frontend/src/pages/SearchResultsPage.tsx` (filter banner + `InlineStatus` import)
- `frontend/src/features/workspace-dashboard/components/WorkspaceDashboardPopulated.tsx`
- `frontend/src/shared/components/QuickActionCard.tsx` (optional `data-testid`)
- `docs/frontend-interaction-audit.md` (new)
- `docs/phases/phase-152C.md` (new)
- `docs/phases/phase-152C-summary.md` (this file)
- `frontend/src/features/workspace-dashboard/phase-152C-interactions.test.tsx` (new)
- `frontend/src/pages/phase-152C-search-filter-banner.test.tsx` (new)
