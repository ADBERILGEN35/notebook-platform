# Frontend design reconciliation (Faz 150)

Maps Stitch design folders under `docs/design/` to the React implementation. Status: **implemented** | **partial** | **missing** | **deferred**.

Source of truth: [notebook_platform_final_design_handoff_source_of_truth.md](design/notebook_platform_final_design_handoff_source_of_truth.md), [final_design_handoff_guide_notebook_platform.md](design/final_design_handoff_guide_notebook_platform.md), [responsive_web_handoff_guide_notebook_platform.md](design/responsive_web_handoff_guide_notebook_platform.md).

## Faz 151C changes (AppShell + dashboard layout alignment)

| Area | Change |
|------|--------|
| Global surface | `body` background `#f5f7fb` → `#fcf8ff` (design `surface` token) |
| SideNav | 288px → 240px, NP rosette + "Enterprise Workspace" subtitle, primary `New Notebook` CTA, compact icon+label nav (Workspaces collapsible / Search / Notifications / Settings / Admin), sticky Support + Sign Out at the bottom (logout mutation moved from `UserMenu`) |
| TopNav | rounded-full search pill (left), `Drafts` / `Shared` / `Archived` secondary nav linking to `/app/search?filter=…`, utility (NotificationBell), `Invite Team` CTA (routes to `/members` when workspace is active, disabled otherwise), `UserMenu` avatar |
| Canvas width | `ResponsiveContent` new `2xl` (`max-w-[1280px]`); WorkspaceHub uses it for breathing room |
| Tests | `app-shell.test.tsx` rewritten + `phase-151c-visual-alignment.test.tsx` (7 new assertions) |

## Faz 151B changes (visual mismatch fix)

| Area | Change |
|------|--------|
| Workspace dashboard empty | New `WorkspaceDashboardHero` (display-lg, decorative blur, primary "Create New Workspace" + secondary "Browse Discovery"), `WorkspaceQuickActions` rebuilt as tonal icon cards (Quick Note + Invite Member), `RecentlyViewedSection` with tiered-opacity skeleton + real-notebook variant |
| Onboarding integration | `OnboardingWizard` full-screen branch removed from `WorkspaceHubPage`. New `GettingStartedPanel` embedded inside empty dashboard above hero; collapsible + dismissable; preserves `localStorage` decision (no new backend service) |
| Cleanup | Removed obsolete `WorkspaceGettingStartedCard` and `RecentlyViewedPlaceholder` (replaced by hero + section) |
| Test ids | `workspace-dashboard-empty`, `getting-started-panel`, `recently-viewed-list` |

## Faz 151A changes

| Area | Change |
|------|--------|
| Workspace dashboard | `WorkspaceDashboardEmpty` / `WorkspaceDashboardPopulated` — enterprise bento layout, API-driven |
| Design assets | `workspace_dashboard_empty` / `_populated`: `code.html` only (**screen.png missing**) |
| Test ids | `workspace-dashboard-empty` / `workspace-dashboard-populated` |

## Faz 150 changes

| Area | Change |
|------|--------|
| Onboarding | `OnboardingWizard` on `/app` when no workspaces + `localStorage` completion |
| Workspace hub | Onboarding integration preserved; hub delegates to dashboard components (Faz 151A) |
| GitOps diff | Unified mobile `compact`, empty diff `role="alert"`, `DiffLine` sr-only labels |
| RBAC override | `role="alert"` on high-risk banner, `aria-label` on summary |
| Conflict modal | Sticky mobile actions, desktop `role="alertdialog"` |

## Onboarding & workspace

| Design folder | Status | Implementation |
|---------------|--------|----------------|
| `welcome_identity_verification_notebook_platform` | **partial** → polished | `OnboardingWizard` step 1 |
| `create_your_workspace_notebook_platform` | **partial** → polished | Wizard step 2 + hub empty create |
| `first_notebook_setup_notebook_platform` | **partial** | Wizard step 3 (copy; API-driven notebook later) |
| `your_first_note_notebook_platform` | **partial** | Wizard step 4 |
| `workspace_dashboard_empty` | **implemented** | `WorkspaceDashboardEmpty` — hero, CTA, quick actions, trust panel, skeleton |
| `workspace_dashboard_populated` | **implemented** | `WorkspaceDashboardPopulated` — cards, recent work, health summary |

## Editor, sync, search (prior phases)

| Design folder | Status | Notes |
|---------------|--------|-------|
| `note_editor_notebook_platform` | implemented | Faz 140 |
| `sync_conflict_resolution_flow` | implemented | `NoteConflictResolutionDialog` |
| `conflict_resolution_modal` | **polished** | Faz 150 a11y/actions |
| `global_search_overlay_notebook_platform` | implemented | Faz 141 |
| `login_notebook_platform` / `register_*` | implemented | Faz 138 |

## Admin / GitOps

| Design folder | Status | Notes |
|---------------|--------|-------|
| `gitops_yaml_diff_viewer_desktop` | implemented | Side-by-side `md+` |
| `gitops_yaml_diff_viewer_mobile` | **polished** | Unified default, `compact`, mobile footer on diff page |
| `gitops_diff_viewer_react_spec` | **polished** | `GitOpsDiffViewer` + `DiffLine` |
| `gitops_errors_accessibility_spec` | **polished** | Empty diff alert, region labels, non-color-only markers |
| `rbac_gitops_override_workflow` | **polished** | `RbacOverrideDiffPanel` |
| `change_requests_notebook_platform` | implemented | Faz 143 |
| Admin retention/notification/dead-letter | implemented | Faz 144 |

## Intentionally deferred

| Item | Reason |
|------|--------|
| Product tour overlay | Out of v1 handoff scope |
| Native mobile apps | Responsive web only |
| Real-time whiteboard | Out of scope |
| Full notebook create API in onboarding | No new backend endpoints; wizard explains sidebar flow |
| Pixel-perfect Stitch HTML parity | Token-aligned polish only |

## Sign-off impact

Frontend remains **GO_WITH_ACCEPTED_RISKS**. New accepted risk **AR-FE-150-14**: onboarding uses `localStorage` + workspace API only (no backend onboarding service).
