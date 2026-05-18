import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useState } from 'react'
import { createWorkspace, listWorkspaces } from '../features/workspaces/workspace-api'
import { listNotebooks } from '../features/notebooks/notebook-api'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { PageHeader } from '../shared/components/PageHeader'
import { PageSection } from '../shared/components/PageSection'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { EmptyState } from '../shared/components/EmptyState'
import { ErrorState } from '../shared/components/ErrorState'
import { LoadingState } from '../shared/components/LoadingState'
import { WorkspaceCard } from '../shared/components/WorkspaceCard'
import { QuickActionCard } from '../shared/components/QuickActionCard'
import { Button } from '../shared/components/Button'
import { Input } from '../shared/components/Input'
import { BookIcon } from '../features/auth/components/AuthIcons'

export function WorkspaceHubPage() {
  const navigate = useNavigate()
  const params = useParams()
  const routeWorkspaceId = params.workspaceId
  const activeWorkspaceId = useWorkspaceStore((state) => state.activeWorkspaceId)
  const setActiveWorkspaceId = useWorkspaceStore((state) => state.setActiveWorkspaceId)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')

  const workspaceQuery = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => listWorkspaces(0, 20),
  })

  const focusWorkspaceId = routeWorkspaceId || activeWorkspaceId

  const notebooksQuery = useQuery({
    queryKey: ['notebooks', focusWorkspaceId],
    queryFn: () => listNotebooks(focusWorkspaceId!, 0, 6),
    enabled: Boolean(focusWorkspaceId),
  })

  const createMutation = useMutation({
    mutationFn: () => createWorkspace({ name: newWorkspaceName, type: 'TEAM' }),
    onSuccess: (workspace) => {
      setNewWorkspaceName('')
      setActiveWorkspaceId(workspace.id)
      void workspaceQuery.refetch()
      navigate(`/app/workspaces/${workspace.id}`)
    },
  })

  if (workspaceQuery.isLoading) {
    return (
      <ResponsiveContent>
        <LoadingState label="Loading workspaces…" />
      </ResponsiveContent>
    )
  }

  if (workspaceQuery.isError) {
    return (
      <ResponsiveContent>
        <ErrorState title="Could not load workspaces" error={workspaceQuery.error} />
      </ResponsiveContent>
    )
  }

  const workspaces = workspaceQuery.data?.items ?? []

  if (!workspaces.length) {
    return (
      <ResponsiveContent>
        <PageHeader title="Workspace hub" subtitle="Organize notebooks and notes by workspace." />
        <EmptyState
          title="No workspaces yet"
          message="Create your first workspace to start capturing knowledge with your team."
          icon={<BookIcon className="h-10 w-10" />}
          actions={
            <div className="mx-auto flex max-w-sm flex-col gap-2">
              <Input
                placeholder="Workspace name"
                value={newWorkspaceName}
                onChange={(event) => setNewWorkspaceName(event.target.value)}
                aria-label="New workspace name"
              />
              <Button
                className="bg-primary text-white hover:bg-primary-container"
                disabled={!newWorkspaceName.trim() || createMutation.isPending}
                onClick={() => createMutation.mutate()}
              >
                Create workspace
              </Button>
            </div>
          }
        />
        {createMutation.isError ? <ErrorState error={createMutation.error} className="mt-4" /> : null}
      </ResponsiveContent>
    )
  }

  const notebooks = notebooksQuery.data?.items ?? []

  return (
    <ResponsiveContent>
      <PageHeader
        title="Workspace hub"
        subtitle="Pick a workspace, jump into notebooks, or create something new."
        actions={
          <Button
            className="bg-primary text-white hover:bg-primary-container"
            onClick={() => navigate('/app/search')}
            type="button"
          >
            Search
          </Button>
        }
      />

      <PageSection title="Quick actions" description="Common tasks for your active workspace.">
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <li>
            <QuickActionCard
              title="New notebook"
              description="Start a notebook in the active workspace."
              disabled={!focusWorkspaceId}
              onClick={() => focusWorkspaceId && navigate(`/app/workspaces/${focusWorkspaceId}`)}
            />
          </li>
          <li>
            <QuickActionCard
              title="Search"
              description="Find notes across your workspace."
              onClick={() => navigate('/app/search')}
            />
          </li>
          <li>
            <QuickActionCard
              title="Notifications"
              description="Review mentions and activity."
              onClick={() => navigate('/app/notifications')}
            />
          </li>
        </ul>
      </PageSection>

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

      <PageSection
        title="Recent notebooks"
        description={
          focusWorkspaceId
            ? 'Recently updated notebooks in the active workspace.'
            : 'Select a workspace to see notebooks.'
        }
      >
        {focusWorkspaceId && notebooksQuery.isLoading ? <LoadingState label="Loading notebooks…" /> : null}
        {focusWorkspaceId && notebooks.length === 0 && !notebooksQuery.isLoading ? (
          <EmptyState
            title="No notebooks yet"
            message="Create a notebook from the sidebar or open workspace settings."
            actions={
              <Button type="button" onClick={() => navigate(`/app/workspaces/${focusWorkspaceId}`)}>
                Manage workspace
              </Button>
            }
          />
        ) : null}
        {notebooks.length > 0 ? (
          <ul className="divide-y divide-outline-variant rounded-xl border border-outline-variant bg-surface-container-lowest">
            {notebooks.map((notebook) => (
              <li key={notebook.id}>
                <Link
                  to={`/app/notebooks/${notebook.id}`}
                  className="flex items-center justify-between px-4 py-3 text-body-md hover:bg-surface-container-low focus-visible:bg-surface-container-low focus-visible:outline-none"
                >
                  <span className="font-medium text-on-surface">{notebook.name}</span>
                  <span className="text-label-md text-on-surface-variant">Open</span>
                </Link>
              </li>
            ))}
          </ul>
        ) : null}
        {!focusWorkspaceId ? (
          <p className="text-body-md text-on-surface-variant">Choose a workspace card above to load recent notebooks.</p>
        ) : null}
      </PageSection>

      <PageSection title="Create workspace">
        <div className="flex max-w-md flex-col gap-2 sm:flex-row">
          <Input
            placeholder="Team workspace name"
            value={newWorkspaceName}
            onChange={(event) => setNewWorkspaceName(event.target.value)}
            aria-label="Workspace name"
          />
          <Button
            className="shrink-0 bg-primary text-white hover:bg-primary-container"
            disabled={!newWorkspaceName.trim() || createMutation.isPending}
            onClick={() => createMutation.mutate()}
          >
            Add workspace
          </Button>
        </div>
        {createMutation.isError ? <ErrorState error={createMutation.error} /> : null}
      </PageSection>
    </ResponsiveContent>
  )
}
