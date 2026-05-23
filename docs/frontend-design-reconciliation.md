# Frontend design reconciliation (Faz 150)

Maps Stitch design folders under `docs/design/` to the React implementation. Status: **implemented** | **partial** | **missing** | **deferred**.

Source of truth: [notebook_platform_final_design_handoff_source_of_truth.md](design/notebook_platform_final_design_handoff_source_of_truth.md), [final_design_handoff_guide_notebook_platform.md](design/final_design_handoff_guide_notebook_platform.md), [responsive_web_handoff_guide_notebook_platform.md](design/responsive_web_handoff_guide_notebook_platform.md).

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
