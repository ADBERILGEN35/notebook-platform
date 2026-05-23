# Frontend Design System (App)

Kaynak tasarım: [design/notebook_platform_design_system/DESIGN.md](design/notebook_platform_design_system/DESIGN.md).

## Token’lar (Tailwind)

`frontend/tailwind.config.ts` — Faz 138’de auth için eklenen semantic renkler AppShell’de kullanılır:

- Surfaces: `surface`, `surface-container-*`, `on-surface`, `on-surface-variant`
- Primary: `primary`, `primary-container`, `primary-fixed`
- Outline: `outline`, `outline-variant`
- Error: `error-container`
- Typography: `font-display`, `text-headline-sm`, `text-body-md`, `text-label-md`
- Shadow: `shadow-card`, `shadow-auth-card`

## Layout pattern’leri

| Pattern | Bileşen | Kullanım |
|---------|---------|----------|
| App chrome | `AppShell` | Authenticated routes |
| Page title | `PageHeader` | Hub, settings, admin |
| Section | `PageSection` | Hub quick actions, lists |
| Content width | `ResponsiveContent` | Hub main column |
| Empty / load / error | `EmptyState`, `LoadingState`, `ErrorState` | API-driven pages |

## App shell spacing

- Desktop sidebar: `w-72`, border `outline-variant`
- Main padding: `p-3 sm:p-4 lg:p-6`
- Mobile: bottom nav `pb-16`, drawer overlay

## Bileşen durumları

- **Focus:** `focus-visible:outline` + `outline-primary` (keyboard)
- **Active nav:** `bg-primary-fixed` + `text-primary`
- **Cards:** `rounded-xl`, `border-outline-variant`, hover `border-primary/30`

## Accessibility

- Landmarks: `aside` (SideNav), `header` (TopNav), `main#main-content`
- `aria-label` on navigation regions and icon-only controls
- Workspace list: `aria-current` on active workspace button
- Empty states: `aria-label` on section title

## Auth vs app

- Auth: `features/auth/components/AuthShell` — centered card, marketing layout
- App: `AppShell` — full viewport, sidebar + topbar

Harici CDN Tailwind/Material Symbols kullanılmaz; fontlar `index.html` Google Fonts link ile.

## Faz 140 patterns

| Pattern | Components |
|---------|------------|
| Settings page | `SettingsSection`, `InfoRow`, `DangerZoneCard` |
| Members | `MemberRow`, `RoleBadge`, invite/role modals |
| Editor chrome | `NoteEditorShell`, `NoteEditorHeader`, `SharePanel` |
| History | `VersionHistoryPanel`, `ActivityTimelinePanel`, `TimelineItem` |
| Access | `AccessDeniedState`, `AccessRequestPanel` |
| Notifications | `NotificationCenterPage` + `SectionCard` inbox |

## Faz 141 patterns

| Pattern | Components |
|---------|------------|
| Command search | `GlobalSearchOverlay`, Ctrl+K in AppShell |
| Search results | `SearchResultCard`, `SearchFilterPanel`, `SearchPreviewDrawer` |
| Discovery | `DiscoveryCard`, saved/recent in localStorage |
| Settings chrome | `SettingsLayout`, `SettingsNav`, `PreferenceToggle` |
| Security | `SessionRow`, `SecurityMethodCard`, `ConfirmActionModal` |
| Offline | `SyncHealthCard`, `LocalDataUsageCard`, `ClearLocalDataDialog` |

## Faz 142 patterns (admin)

| Pattern | Components |
|---------|------------|
| Admin page chrome | `AdminPageShell`, `AdminRunbookLink` |
| Health / risk | `AdminHealthCard`, `AdminRiskBadge`, `AdminOverviewCard` |
| Setup readiness | `AdminSetupChecklist` + `buildSetupChecklistItems` |
| Diagnostics shell | `AdminSearchPanel`, `AdminDiagnosticPanel` |
| Identity | `IdentityStatusCard`, `SsoDiagnosticCard`, `ScimProvisioningCard` |
| RBAC mapping | `RoleMappingTable`, `RoleMappingWarningCard` |
| Break-glass | `BreakGlassStatusCard`, `BreakGlassRevocationSummary`, `maskSessionId` |
| Audit (extracted) | `AuditEventTable`, `AuditEventDetailDrawer` + `metadata-mask` |

## Faz 143 patterns (change requests / GitOps)

| Pattern | Components |
|---------|------------|
| Status badges | `ChangeRequestStatusBadge`, `SeverityBadge`, `OperationTypeBadge`, `GitOpsStateBadge` |
| Approval | `ChangeRequestTimeline`, `ApprovalGatePanel` |
| GitOps | `GitOpsPrStateCard`, `DryRunWarningList`, `GitOpsDisabledBanner` |
| Diff | `GitOpsDiffViewer`, `DiffLine`, `DiffFileHeader`, `maskDiffLineContent` |
| RBAC | `RbacOverrideDiffPanel` (PLATFORM_ADMIN warning) |

## Faz 144 patterns (notification ops / retention)

| Pattern | Components |
|---------|------------|
| Metrics | `AdminMetricCard` (aggregate-only) |
| Dead-letter | `DeadLetterEventTable`, `DuplicateRiskBadge`, `RequeueEligibilityChecklist` |
| Retention | `RetentionTargetTable`, `RetentionWarningChip`, `RetentionServiceSummaryCard` |
| Legal hold | `LegalHoldCard` + `sanitizeLegalHoldReason` |
| Purge | `PurgeConfirmationDialog`, `PurgeResultSummary`, `purge-result-storage` |
| Privacy | `maskRecipientHash`, `maskActorId`, `sanitizeDeadLetterForDisplay` |

## Faz 145 patterns (quality / a11y / responsive)

| Pattern | Usage |
|---------|--------|
| Skip link | `SkipToMain` → `#main-content` / `#auth-main` |
| Focus | Global `:focus-visible` in `index.css`; component `focus-visible:ring-*` |
| Dense tables | `ResponsiveTableShell` — horizontal scroll + `role="region"` |
| Modals | `Modal` / `ResponsiveDrawer` — Escape, `aria-modal`, labelled close |
| Mobile diff | GitOps side-by-side `hidden md:block`; unified default |
| E2E | `tests/e2e/helpers/no-secrets.ts` — no JWT/Bearer in DOM |

## Faz 146 RC gate

| Artifact | Purpose |
|----------|---------|
| `ci-frontend-rc-readiness.sh` | Single local/CI orchestrator |
| `build_frontend_rc_readiness_report.py` | Sanitized JSON/Markdown + route inventory |
| `frontend-rc-readiness.yml` | GitHub Actions with Playwright browser install |

## Faz 147 RC sign-off + visual QA

| Artifact | Purpose |
|----------|---------|
| [frontend-release-candidate-signoff.md](../frontend-release-candidate-signoff.md) | GO/NO_GO rules + required RC artifacts |
| [frontend-release-visual-qa-checklist.md](../frontend-release-visual-qa-checklist.md) | Manual QA (unchecked by default) |
| `generate-frontend-rc-signoff-template.sh` | Placeholder sign-off markdown |

## Faz 150 Design reconciliation

| Artifact | Purpose |
|----------|---------|
| [frontend-design-reconciliation.md](../frontend-design-reconciliation.md) | Stitch ↔ implementation inventory |
| `OnboardingWizard` | Hub conditional stepper (no new routes) |
| `GitOpsDiffViewer` `compact` | Mobile unified diff + a11y labels |

## Faz 149 Visual QA execution

| Artifact | Purpose |
|----------|---------|
| `tests/e2e/visual-qa-signoff.spec.ts` | Production-preview visual QA automation |
| `E2E_USE_PREVIEW=1` | `playwright.config.ts` serves `vite preview` :4173 |
| `stub-non-admin-session.ts` | AdminGate denial without bypass |
