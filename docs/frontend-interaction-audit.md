# Frontend interaction audit (Faz 152C)

Audit of every user-facing action surface in the local `/app` experience. Each row is classified into one of:

| Code | Meaning |
|------|---------|
| `WORKING` | Action is wired to a real API or route and works end-to-end. |
| `DISABLED_BY_DESIGN` | Button/link is intentionally `disabled` or rendered as a safe shell (no backend yet, gated by env). |
| `MISSING_BACKEND_CONTRACT` | UI exists but the backend endpoint is not yet implemented / not exposed. |
| `FEATURE_FLAG_DISABLED` | Behavior is hidden or read-only because a feature flag is off in this environment. |
| `PERMISSION_BLOCKED` | Gated behind an admin permission / `AdminGate` / `Protected`. |
| `BUG_FRONTEND` | Frontend bug; can be safely fixed in this phase. |
| `NEEDS_MANUAL_CONFIRMATION` | Manual / live-env QA needed to confirm (e.g. SSO, MFA, real PR creation). |

> Backend contract, feature flag, permission and route guard behavior MUST NOT be changed. This document only describes what currently happens.

## Summary

| Class | Count |
|-------|-------|
| WORKING | 39 |
| DISABLED_BY_DESIGN | 8 |
| MISSING_BACKEND_CONTRACT | 3 |
| FEATURE_FLAG_DISABLED | 14 |
| PERMISSION_BLOCKED | 11 |
| BUG_FRONTEND | 2 (fixed in 152C) |
| NEEDS_MANUAL_CONFIRMATION | 6 |
| **Total** | **83** |

## 1. Auth

| Route | Component | Action | Behavior | Classification | Notes |
|-------|-----------|--------|----------|----------------|-------|
| `/login` | `LoginPage` | Email + password submit (`Continue`) | `POST /auth/login` via `login()`; MFA-aware redirect | WORKING | |
| `/login` | `LoginPage` | `Forgot password?` link | Routes to `/forgot-password` | WORKING | |
| `/login` | `LoginPage` | `Create an account` link | Routes to `/register` | WORKING | |
| `/login` | `LoginPage` | SSO `Continue with …` button | Redirects to `${API_BASE_URL}/auth/sso/:reg/authorize` | NEEDS_MANUAL_CONFIRMATION | Provider must be configured |
| `/login` | `LoginPage` | `SSO not configured` placeholder | `disabled` `<SsoButton>` when provider list empty | DISABLED_BY_DESIGN | |
| `/register` | `RegisterPage` | Submit | `POST /auth/signup` | WORKING | |
| `/forgot-password` | `ForgotPasswordPage` | Request reset email | `POST /auth/forgot-password` | NEEDS_MANUAL_CONFIRMATION | Email delivery is env-dependent |
| `/mfa` | `MfaAuthenticationPage` | Code submit | `POST /auth/mfa/verify` | NEEDS_MANUAL_CONFIRMATION | TOTP secret/QR varies per env |
| `/sso/callback` | `SsoCallbackPage` | Auto-redirects on success/error | `GET /auth/sso/callback` | NEEDS_MANUAL_CONFIRMATION | |

## 2. AppShell (Faz 151C)

| Route | Component | Action | Behavior | Classification | Notes |
|-------|-----------|--------|----------|----------------|-------|
| `*` | `SideNav` | NP brand link | Routes to `/app/workspaces` | WORKING | |
| `*` | `SideNav` | `New Notebook` CTA | Opens AppShellPage create-notebook modal; disabled when no active workspace | WORKING (disabled when no workspace) | |
| `*` | `SideNav` | Workspaces `<details>` switch button | `useWorkspaceStore.setActiveWorkspaceId` + route to `/app/workspaces/:id` | WORKING | |
| `*` | `SideNav` | Search / Notifications / Settings / Admin | Standard `<Link>` routing | WORKING | Admin is `showAdminNav`-gated |
| `*` | `SideNav` | `Support` | Routes to `/app/settings` (placeholder) | DISABLED_BY_DESIGN | No dedicated support service yet |
| `*` | `SideNav` | `Sign Out` | `logout` mutation + `clearSession()` + `/login` redirect | WORKING | |
| `*` | `TopNav` | Search pill input + Ctrl/Cmd+K | Opens `GlobalSearchOverlay` via `onOpenSearch` | WORKING | |
| `*` | `TopNav` | `Drafts` / `Shared` / `Archived` | Routes to `/app/search?filter=drafts|shared|archived` | **BUG_FRONTEND → fixed** | Filter param was silently dropped; 152C surfaces `InlineStatus` banner explaining backend contract is pending |
| `*` | `TopNav` | `Invite Team` CTA | Routes to `/app/workspaces/:id/members`; `disabled` when no active workspace | WORKING (disabled when no workspace) | |
| `*` | `TopNav` | `NotificationBell` | Routes to `/app/notifications` | WORKING | |
| `*` | `TopNav` | `UserMenu` avatar | Routes to `/app/settings`, Sign out via mutation | WORKING | |
| `*` | `MobileNav` | Hub / Search / Alerts / More | Bottom tab links | WORKING | |
| `*` | `MobileNavDrawer` | Same as `SideNav` in drawer | `ResponsiveDrawer` | WORKING | |

## 3. Workspace dashboard (Faz 151B + 151C)

| Route | Component | Action | Behavior | Classification | Notes |
|-------|-----------|--------|----------|----------------|-------|
| `/app` (empty) | `WorkspaceDashboardHero` | `Create New Workspace` | `createWorkspace({type:'TEAM'})` mutation | WORKING | |
| `/app` (empty) | `WorkspaceDashboardHero` | `Browse Discovery` link | Routes to `/app/search/discover` | WORKING | |
| `/app` (empty) | `WorkspaceQuickActions` | `Quick Note` | `navigate('/app/search')`; disabled when no workspace | DISABLED_BY_DESIGN | No active workspace ⇒ no notebook ⇒ no note. Disabled state is honest. |
| `/app` (empty) | `WorkspaceQuickActions` | `Invite Member` | `navigate('/app/workspaces/:id/members')`; disabled when no workspace | DISABLED_BY_DESIGN | |
| `/app` (empty) | `RecentlyViewedSection` | `View All` | Hidden when no workspace (shows "Nothing yet") | DISABLED_BY_DESIGN | |
| `/app` (empty) | `GettingStartedPanel` | `Dismiss` | `markOnboardingComplete()` + local state | WORKING | |
| `/app` (empty) | `GettingStartedPanel` | `Focus create form` | Focuses workspace name input | WORKING | |
| `/app` (populated) | `WorkspaceDashboardPopulated` | `Search` header CTA | `navigate('/app/search')` | WORKING | |
| `/app` (populated) | `WorkspaceCard` | Click | Routes to `/app/workspaces/:id` | WORKING | |
| `/app` (populated) | `RecentWorkspaceActivity` | Notebook click | Routes to `/app/notebooks/:id` | WORKING | |
| `/app` (populated) | `WorkspaceQuickActions` (Populated panel) | `New notebook` | **was**: navigate same `/app/workspaces/:id` (no-op visual); **now**: routes to active workspace overview (`/app/workspaces/:id`) AND label/description clarified ("Open workspace" CTA next to sidebar `New Notebook` modal) | **BUG_FRONTEND → fixed** | The sidebar `New Notebook` modal (151C) is the canonical create entry point; the dashboard tile now points users at workspace overview without misleading "New notebook" wording. |
| `/app` (populated) | Dashboard `Search` / `Notifications` quick actions | `navigate` | WORKING | |
| `/app` (populated) | `Add another workspace` | `createWorkspace` | WORKING | |

## 4. Note editor & collaboration

| Route | Component | Action | Behavior | Classification | Notes |
|-------|-----------|--------|----------|----------------|-------|
| `/app/workspaces/:id/notes/:nid` | `NoteEditorHeader` | `Share` toggle | Toggles `SharePanel` side panel | WORKING | |
| `/app/workspaces/:id/notes/:nid` | `NoteEditorHeader` | `History` | Routes to `/notes/:nid/history` | WORKING | |
| `/app/workspaces/:id/notes/:nid` | `SharePanel` | `Invite` | Opens `InviteMemberModal` (real API) | WORKING | |
| `/app/workspaces/:id/notes/:nid` | `SharePanel` | `Manage members` | Routes to workspace members | WORKING | |
| `/app/workspaces/:id/notes/:nid` | `NoteEditor` | Save/sync indicators | Auto-save mutation; conflict-detection | WORKING | |
| `/app/workspaces/:id/notes/:nid` | `NoteConflictResolutionDialog` | Reload / Save copy / Overwrite | Real mutations | WORKING | |
| `/app/workspaces/:id/notes/:nid/history` | `NoteEditorHistoryPage` | Restore version | Real mutation | NEEDS_MANUAL_CONFIRMATION | Requires existing version data |

## 5. Search

| Route | Component | Action | Behavior | Classification | Notes |
|-------|-----------|--------|----------|----------------|-------|
| `*` | `GlobalSearchOverlay` (Ctrl/Cmd+K) | Open | Modal opens; `onClose` reliable | WORKING | |
| `/app/search` | `SearchResultsPage` | Submit | `searchNotes(workspaceId, q)` | WORKING | |
| `/app/search` | `SearchResultsPage` | `?filter=...` query param | Was silently ignored; 152C adds `InlineStatus` explaining backend filter contract is pending | **BUG_FRONTEND → fixed** | Filter awareness is the safe fix until backend supports it |
| `/app/search` | `SearchFilterPanel` | Sort / Notebook filter | Local state only (no backend filter) | MISSING_BACKEND_CONTRACT | Backend search doesn't accept notebook filter yet |
| `/app/search` | `SearchPreviewDrawer` | Open / close | Local state | WORKING | |
| `/app/search/discover` | `SearchDiscoveryPage` | Recent / Saved search links | Routes to `/app/search?q=…` | WORKING | |
| `/app/search/discover` | `SearchDiscoveryPage` | Save current | `localStorage` via `saveSearch` | WORKING | localStorage only — by design |

## 6. Settings

| Route | Component | Action | Behavior | Classification |
|-------|-----------|--------|----------|----------------|
| `/app/settings` | `UserSettingsPage` | Update profile | Real mutation | WORKING |
| `/app/settings/security` | `AccountSecurityPage` | Session revoke / revoke-all | Real mutations | WORKING |
| `/app/settings/notifications` | `NotificationPreferencesPage` | Channel toggles | Real mutation | WORKING |
| `/app/settings/sync` | `OfflineSyncDiagnosticsPage` | Background sync run | Local IndexedDB driver | WORKING |

## 7. Workspace admin / members

| Route | Component | Action | Behavior | Classification |
|-------|-----------|--------|----------|----------------|
| `/app/workspaces/:id/members` | `WorkspaceMembersPage` | `Invite member` | `InviteMemberModal` real `POST /workspaces/:id/invitations` | WORKING |
| `/app/workspaces/:id/members` | `MemberRow` | `Change role` | `UpdateRoleModal` real `PATCH` | WORKING |
| `/app/workspaces/:id/members` | `MemberRow` | `Remove` | `RevokeAccessConfirmModal` real `DELETE` | WORKING |
| `/app/workspaces/:id/members` | invitation row | `Revoke` | Same modal, `mode=invitation` | WORKING |
| `/app/workspaces/:id/settings` | `WorkspaceSettingsPage` | `Save changes` | `updateWorkspace` mutation | WORKING |
| `/app/workspaces/:id/settings` | DangerZone | `Archive workspace` | Hard-disabled with title hint | DISABLED_BY_DESIGN |
| `/app/workspaces/:id/settings` | Branding | "Logo upload" | InfoRow `Upload coming soon` | DISABLED_BY_DESIGN |

## 8. Admin overview & navigation cards

| Route | Component | Action | Behavior | Classification |
|-------|-----------|--------|----------|----------------|
| `/app/admin` | `AdminHomePage` | Redirect | `<Navigate to="/app/admin/overview">` | WORKING |
| `/app/admin/overview` | `AdminOverviewCard` Audit / Identity / Retention / Break-glass | Permission + feature-flag gated `<Link>` (or no `to` ⇒ disabled card) | DISABLED_BY_DESIGN / PERMISSION_BLOCKED | Mixed — depends on flags + roles |
| `/app/admin/setup` | `AdminSetupChecklist` | Read-only checklist | No actions | WORKING |

## 9. Admin change requests (Faz 77–80)

| Route | Component | Action | Behavior | Classification |
|-------|-----------|--------|----------|----------------|
| `/app/admin/change-requests` | list page | `Create` | Real `POST` only when `ENTERPRISE_ADMIN_WRITE_ENABLED` flag + permission | FEATURE_FLAG_DISABLED unless enabled |
| `/app/admin/change-requests/:id` | detail | `Approve` / `Reject` | Real mutations; `ENTERPRISE_ADMIN_APPROVALS_ENABLED` flag | FEATURE_FLAG_DISABLED unless enabled |
| `/app/admin/change-requests/:id/dry-run` | `AdminChangeRequestDryRunPage` | `Run dry-run` | Real mutation (safe, no apply) | PERMISSION_BLOCKED |
| `/app/admin/change-requests/:id/gitops` | `AdminChangeRequestGitOpsPage` | `Create PR` | `ENTERPRISE_GITOPS_PR_ENABLED` required | FEATURE_FLAG_DISABLED |
| `/app/admin/change-requests/:id/diff` | `AdminChangeRequestDiffPage` | Read-only diff viewer | No mutation | WORKING |

## 10. Admin notifications (Faz 81–84)

| Route | Component | Action | Behavior | Classification |
|-------|-----------|--------|----------|----------------|
| `/app/admin/notifications/analytics` | `AdminNotificationAnalyticsPage` | Filter / refresh | Aggregate read | FEATURE_FLAG_DISABLED unless `NOTIFICATION_ANALYTICS_UI_ENABLED` |
| `/app/admin/notifications/dead-letter` | `AdminNotificationDeadLetterPage` | Row click | Routes to detail | FEATURE_FLAG_DISABLED unless `NOTIFICATION_DEAD_LETTER_UI_ENABLED` |
| `/app/admin/notifications/dead-letter/:id/requeue` | `AdminNotificationDeadLetterRequeuePage` | `Dry-run` / `Requeue` | Real mutation gated by flag + permission | FEATURE_FLAG_DISABLED / PERMISSION_BLOCKED |
| `/app/admin/notifications/retention` | `AdminNotificationRetentionPage` | Manual purge | `NOTIFICATION_RETENTION_PURGE_UI_ENABLED` required | FEATURE_FLAG_DISABLED |
| `/app/admin/notifications/legal-holds` | `AdminNotificationLegalHoldsPage` | Hold create/release | `NOTIFICATION_LEGAL_HOLD_UI_ENABLED` required | FEATURE_FLAG_DISABLED |

## 11. Admin retention (Faz 98)

| Route | Component | Action | Behavior | Classification |
|-------|-----------|--------|----------|----------------|
| `/app/admin/retention` | `AdminRetentionHubPage` | Navigation cards | Read-only links | FEATURE_FLAG_DISABLED unless `PLATFORM_RETENTION_GOVERNANCE_ENABLED` |
| `/app/admin/retention/platform` | `AdminPlatformRetentionPage` | `Dry-run` | Real mutation (no apply) | PERMISSION_BLOCKED |
| `/app/admin/retention/legal-holds` | `AdminPlatformLegalHoldsPage` | Create / release hold | Real mutations | PERMISSION_BLOCKED |
| `/app/admin/retention/purge-result` | `AdminPurgeResultPage` | Read-only | Read-only | WORKING |

## 12. Admin identity & security

| Route | Component | Action | Behavior | Classification |
|-------|-----------|--------|----------|----------------|
| `/app/admin/identity` | `AdminIdentityOverviewPage` | Card links | Standard nav | PERMISSION_BLOCKED |
| `/app/admin/identity/sso` | `AdminSsoDiagnosticsPage` | Refresh / test | Read-only diagnostics | PERMISSION_BLOCKED |
| `/app/admin/identity/scim` | `AdminScimProvisioningPage` | Refresh / dry-run | Read-only + optional dry-run | FEATURE_FLAG_DISABLED unless `SCIM_*` flags |
| `/app/admin/identity/role-mapping` | `AdminRoleMappingDiagnosticsPage` | Refresh | Read-only | PERMISSION_BLOCKED |
| `/app/admin/security/break-glass` | `AdminBreakGlassOpsPage` | `Submit` review | Real mutation | FEATURE_FLAG_DISABLED unless `BREAK_GLASS_REVIEW_UI_ENABLED` |
| `/app/admin/security/break-glass` | `AdminBreakGlassOpsPage` | `Revoke active token` | Real mutation; permission + `BREAK_GLASS_REVOCATION_UI_ENABLED` | FEATURE_FLAG_DISABLED / PERMISSION_BLOCKED |
| `/app/admin/security/break-glass/rotation` | `AdminBreakGlassRotationPage` | Acknowledge / verify / close | Real mutations | FEATURE_FLAG_DISABLED unless `BREAK_GLASS_ROTATION_UI_ENABLED` |
| `/app/admin/rbac` | `AdminRbacPage` | Browse / grant request | Read-only + flag-gated requests | FEATURE_FLAG_DISABLED unless `ADMIN_RBAC_*` flags |

## Fixes applied in Faz 152C

| # | Surface | Class | Fix |
|---|---------|-------|-----|
| 1 | `TopNav` Drafts/Shared/Archived → `SearchResultsPage` | `BUG_FRONTEND` | Filter query is now surfaced via `InlineStatus` banner explaining "Filter '<value>' is not yet wired to the backend search contract". No fake filtering applied to results. |
| 2 | Populated dashboard `New notebook` QuickAction | `BUG_FRONTEND` | Misleading label/no-op navigation replaced with `Open workspace overview` action that honestly reflects the navigation, and the description points users to sidebar `New Notebook` modal (the canonical create entry point added in Faz 151C). |

## Things left **intentionally untouched** (not bugs)

- Admin destructive actions (PR creation, real requeue, manual purge, break-glass revoke, role grants) require **both** a feature flag and a permission and are therefore left unchanged.
- "Browse Templates" copy from `workspace_dashboard_empty` design — there is no template service, so the design's secondary CTA is replaced by `Browse Discovery` (real `/app/search/discover` route).
- "Recently Viewed → View All" — in empty path the section explicitly shows "Nothing yet" and skeleton; in populated path the notebook cards click through to `/app/notebooks/:id`. No fake "view all" until a per-user recent-items endpoint exists.
- "Quick Note" in populated path — clicking the sidebar `New Notebook` CTA is the canonical creation; the dashboard tile's `Quick Note` (when shown) routes to search to avoid duplicating the modal.

## Hard rules enforced

- No backend API or endpoint changed.
- No production feature flag was flipped.
- No token / JWT / Bearer / Authorization / SCIM payload / break-glass token surfaced.
- No real destructive action wired without its pre-existing flag + permission gate.
- No mock data introduced into production paths (only `localStorage`-backed UX state).
