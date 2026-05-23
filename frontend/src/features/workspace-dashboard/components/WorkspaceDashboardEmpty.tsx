import { Link } from 'react-router-dom'
import { DashboardBreadcrumb } from './DashboardBreadcrumb'
import { EnterpriseTrustPanel } from './EnterpriseTrustPanel'
import { RecentlyViewedPlaceholder } from './RecentlyViewedPlaceholder'
import { WorkspaceGettingStartedCard } from './WorkspaceGettingStartedCard'
import { WorkspaceQuickActions } from './WorkspaceQuickActions'

type WorkspaceDashboardEmptyProps = {
  workspaceName: string
  onWorkspaceNameChange: (value: string) => void
  onCreateWorkspace: () => void
  createPending: boolean
  createError: boolean
  error: unknown
  onGuidedSetup?: () => void
  showGuidedSetup: boolean
  onSearch: () => void
  onNotifications: () => void
}

export function WorkspaceDashboardEmpty({
  workspaceName,
  onWorkspaceNameChange,
  onCreateWorkspace,
  createPending,
  createError,
  error,
  onGuidedSetup,
  showGuidedSetup,
  onSearch,
  onNotifications,
}: WorkspaceDashboardEmptyProps) {
  return (
    <div data-testid="workspace-dashboard-empty" className="space-y-8">
      <DashboardBreadcrumb />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="lg:col-span-8">
          <WorkspaceGettingStartedCard
            workspaceName={workspaceName}
            onWorkspaceNameChange={onWorkspaceNameChange}
            onCreateWorkspace={onCreateWorkspace}
            createPending={createPending}
            createError={createError}
            error={error}
            onGuidedSetup={onGuidedSetup}
            showGuidedSetup={showGuidedSetup}
            secondaryAction={
              <Link
                to="/app/search"
                className="inline-flex items-center rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-body-md font-medium text-on-surface hover:bg-surface-container-low focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                Browse discovery
              </Link>
            }
          />
        </div>
        <div className="lg:col-span-4">
          <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-card">
            <WorkspaceQuickActions
              focusWorkspaceId={null}
              onSearch={onSearch}
              onNotifications={onNotifications}
              title="Quick actions"
            />
          </div>
        </div>
      </div>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <RecentlyViewedPlaceholder />
        </div>
        <EnterpriseTrustPanel />
      </div>
    </div>
  )
}
