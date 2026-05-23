import type { Notebook, Workspace } from '../../../shared/types/api'
import { PageHeader } from '../../../shared/components/PageHeader'
import { PageSection } from '../../../shared/components/PageSection'
import { WorkspaceCard } from '../../../shared/components/WorkspaceCard'
import { QuickActionCard } from '../../../shared/components/QuickActionCard'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { ErrorState } from '../../../shared/components/ErrorState'
import { DashboardBreadcrumb } from './DashboardBreadcrumb'
import { RecentWorkspaceActivity } from './RecentWorkspaceActivity'
import { WorkspaceHealthSummary } from './WorkspaceHealthSummary'

type WorkspaceDashboardPopulatedProps = {
  userName?: string | null
  workspaces: Workspace[]
  notebooks: Notebook[]
  focusWorkspaceId: string | null
  activeWorkspace?: Workspace
  notebooksLoading: boolean
  newWorkspaceName: string
  onWorkspaceNameChange: (value: string) => void
  onCreateWorkspace: () => void
  createPending: boolean
  createError: boolean
  error: unknown
  onSearch: () => void
  onNotifications: () => void
  onNewNotebook: () => void
  onManageWorkspace: () => void
}

export function WorkspaceDashboardPopulated({
  userName,
  workspaces,
  notebooks,
  focusWorkspaceId,
  activeWorkspace,
  notebooksLoading,
  newWorkspaceName,
  onWorkspaceNameChange,
  onCreateWorkspace,
  createPending,
  createError,
  error,
  onSearch,
  onNotifications,
  onNewNotebook,
  onManageWorkspace,
}: WorkspaceDashboardPopulatedProps) {
  const greeting = userName?.trim()
    ? `Welcome back, ${userName.split(' ')[0]}`
    : activeWorkspace
      ? `Welcome back — ${activeWorkspace.name}`
      : 'Welcome back'

  return (
    <div data-testid="workspace-dashboard-populated" className="space-y-8">
      <DashboardBreadcrumb />
      <PageHeader
        title={greeting}
        subtitle="Pick a workspace, jump into notebooks, or create something new."
        actions={
          <Button
            type="button"
            className="bg-primary text-on-primary hover:bg-primary-container"
            onClick={onSearch}
          >
            Search
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="space-y-8 lg:col-span-8">
          <PageSection title="Your workspaces" description="Select a workspace to focus the sidebar and notebooks.">
            <ul className="grid gap-4 sm:grid-cols-2">
              {workspaces.map((workspace) => (
                <li key={workspace.id}>
                  <WorkspaceCard
                    workspace={workspace}
                    isActive={workspace.id === focusWorkspaceId}
                    notebookCount={workspace.id === focusWorkspaceId ? notebooks.length : undefined}
                  />
                </li>
              ))}
            </ul>
          </PageSection>

          <RecentWorkspaceActivity
            notebooks={notebooks}
            workspaces={workspaces}
            focusWorkspaceId={focusWorkspaceId}
            isLoading={notebooksLoading}
            onManageWorkspace={onManageWorkspace}
          />
        </div>

        <div className="space-y-6 lg:col-span-4">
          <WorkspaceHealthSummary
            workspaceCount={workspaces.length}
            notebookCount={focusWorkspaceId ? notebooks.length : undefined}
          />
          <PageSection title="Quick actions" description="Common tasks for your active workspace.">
            <ul className="flex flex-col gap-2">
              <li>
                <QuickActionCard
                  title="New notebook"
                  description="Start a notebook in the active workspace."
                  icon={<span aria-hidden>📓</span>}
                  disabled={!focusWorkspaceId}
                  onClick={onNewNotebook}
                />
              </li>
              <li>
                <QuickActionCard
                  title="Search"
                  description="Find notes across your workspace."
                  icon={<span aria-hidden>🔍</span>}
                  onClick={onSearch}
                />
              </li>
              <li>
                <QuickActionCard
                  title="Notifications"
                  description="Review mentions and activity."
                  icon={<span aria-hidden>🔔</span>}
                  onClick={onNotifications}
                />
              </li>
            </ul>
          </PageSection>
        </div>
      </div>

      <PageSection title="Add another workspace">
        <div className="flex max-w-md flex-col gap-2 sm:flex-row">
          <Input
            placeholder="Team workspace name"
            value={newWorkspaceName}
            onChange={(event) => onWorkspaceNameChange(event.target.value)}
            aria-label="Workspace name"
          />
          <Button
            type="button"
            className="shrink-0 bg-primary text-on-primary hover:bg-primary-container"
            disabled={!newWorkspaceName.trim() || createPending}
            onClick={onCreateWorkspace}
          >
            Add workspace
          </Button>
        </div>
        {createError && error ? <ErrorState error={error} className="mt-4" /> : null}
      </PageSection>
    </div>
  )
}
