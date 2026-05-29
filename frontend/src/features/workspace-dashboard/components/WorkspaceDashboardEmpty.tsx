import { Link } from 'react-router-dom'
import { DashboardBreadcrumb } from './DashboardBreadcrumb'
import { EnterpriseTrustPanel } from './EnterpriseTrustPanel'
import { GettingStartedPanel } from './GettingStartedPanel'
import { RecentlyViewedSection } from './RecentlyViewedSection'
import { WorkspaceDashboardHero } from './WorkspaceDashboardHero'
import { WorkspaceQuickActions } from './WorkspaceQuickActions'

type WorkspaceDashboardEmptyProps = {
  workspaceName: string
  onWorkspaceNameChange: (value: string) => void
  onCreateWorkspace: () => void
  createPending: boolean
  createError: boolean
  error: unknown
  showOnboarding: boolean
  onDismissOnboarding: () => void
  onFocusCreate: () => void
  onQuickNote: () => void
  onInviteMember?: () => void
}

/**
 * Empty workspace dashboard layout.
 *
 * Matches docs/design/workspace_dashboard_empty bento layout:
 * - 8/4 hero + quick actions row
 * - Full-width Recently Viewed skeleton
 * - Optional onboarding panel above (embedded, never full-screen)
 */
export function WorkspaceDashboardEmpty({
  workspaceName,
  onWorkspaceNameChange,
  onCreateWorkspace,
  createPending,
  createError,
  error,
  showOnboarding,
  onDismissOnboarding,
  onFocusCreate,
  onQuickNote,
  onInviteMember,
}: WorkspaceDashboardEmptyProps) {
  return (
    <div data-testid="workspace-dashboard-empty" className="space-y-6">
      <DashboardBreadcrumb />
      {showOnboarding ? (
        <GettingStartedPanel
          hasWorkspace={false}
          onDismiss={onDismissOnboarding}
          onFocusCreate={onFocusCreate}
        />
      ) : null}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="lg:col-span-8">
          <WorkspaceDashboardHero
            workspaceName={workspaceName}
            onWorkspaceNameChange={onWorkspaceNameChange}
            onCreateWorkspace={onCreateWorkspace}
            createPending={createPending}
            createError={createError}
            error={error}
            secondaryAction={
              <Link
                to="/app/search/discover"
                className="inline-flex items-center gap-2 rounded-md border border-outline-variant bg-surface-container-lowest px-4 py-2 text-body-md font-medium text-on-surface hover:bg-surface-container-low focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                <span aria-hidden>▦</span>
                Browse Discovery
              </Link>
            }
          />
        </div>
        <div className="lg:col-span-4">
          <WorkspaceQuickActions
            focusWorkspaceId={null}
            onQuickNote={onQuickNote}
            onInviteMember={onInviteMember}
          />
        </div>
      </div>

      <RecentlyViewedSection notebooks={[]} focusWorkspaceId={null} isLoading={false} />

      <EnterpriseTrustPanel />
    </div>
  )
}
