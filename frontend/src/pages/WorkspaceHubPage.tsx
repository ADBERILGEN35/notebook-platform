import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { createWorkspace, listWorkspaces } from '../features/workspaces/workspace-api'
import { listNotebooks } from '../features/notebooks/notebook-api'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { useAuthStore } from '../features/auth/auth-store'
import {
  isOnboardingComplete,
  markOnboardingComplete,
} from '../features/onboarding/onboarding-storage'
import {
  WorkspaceDashboardEmpty,
  WorkspaceDashboardPopulated,
} from '../features/workspace-dashboard/components'
import { PageHeader } from '../shared/components/PageHeader'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { ErrorState } from '../shared/components/ErrorState'
import { LoadingState } from '../shared/components/LoadingState'
import { Button } from '../shared/components/Button'

/**
 * Workspace Hub route handler.
 *
 * Faz 151B fix: previously, new users were routed to a full-screen
 * OnboardingWizard which masked the design's dashboard layout. We now
 * always render the empty/populated dashboard and embed onboarding as a
 * dismissable panel above the hero.
 */
export function WorkspaceHubPage() {
  const navigate = useNavigate()
  const params = useParams()
  const routeWorkspaceId = params.workspaceId
  const user = useAuthStore((state) => state.user)
  const activeWorkspaceId = useWorkspaceStore((state) => state.activeWorkspaceId)
  const setActiveWorkspaceId = useWorkspaceStore((state) => state.setActiveWorkspaceId)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')
  const [onboardingDismissed, setOnboardingDismissed] = useState(false)

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

  const workspaces = workspaceQuery.data?.items ?? []

  useEffect(() => {
    if (workspaceQuery.isSuccess && workspaces.length > 0 && !isOnboardingComplete()) {
      markOnboardingComplete()
    }
  }, [workspaceQuery.isSuccess, workspaces.length])

  if (workspaceQuery.isLoading) {
    return (
      <ResponsiveContent maxWidth="2xl">
        <LoadingState label="Loading workspaces…" />
      </ResponsiveContent>
    )
  }

  if (workspaceQuery.isError) {
    return (
      <ResponsiveContent
        maxWidth="2xl"
        className="flex min-h-[min(28rem,calc(100dvh-11rem))] flex-col justify-center gap-4"
      >
        <PageHeader
          title="Your workspace dashboard"
          subtitle="Workspaces could not be loaded. The workspace service may be offline in local development."
        />
        <ErrorState
          title="Could not load workspaces"
          error={workspaceQuery.error}
          actions={
            <Button type="button" onClick={() => void workspaceQuery.refetch()}>
              Retry
            </Button>
          }
        />
      </ResponsiveContent>
    )
  }

  const showOnboardingPanel = !isOnboardingComplete() && !onboardingDismissed

  if (!workspaces.length) {
    return (
      <ResponsiveContent maxWidth="2xl">
        <WorkspaceDashboardEmpty
          workspaceName={newWorkspaceName}
          onWorkspaceNameChange={setNewWorkspaceName}
          onCreateWorkspace={() => createMutation.mutate()}
          createPending={createMutation.isPending}
          createError={createMutation.isError}
          error={createMutation.error}
          showOnboarding={showOnboardingPanel}
          onDismissOnboarding={() => {
            markOnboardingComplete()
            setOnboardingDismissed(true)
          }}
          onFocusCreate={() => {
            const input = document.querySelector<HTMLInputElement>('input[aria-label="New workspace name"]')
            input?.focus()
          }}
          onQuickNote={() => navigate('/app/search')}
          onInviteMember={() =>
            focusWorkspaceId && navigate(`/app/workspaces/${focusWorkspaceId}/members`)
          }
        />
      </ResponsiveContent>
    )
  }

  const notebooks = notebooksQuery.data?.items ?? []
  const activeWorkspace = workspaces.find((w) => w.id === focusWorkspaceId)

  return (
    <ResponsiveContent maxWidth="2xl">
      <WorkspaceDashboardPopulated
        userName={user?.name}
        workspaces={workspaces}
        notebooks={notebooks}
        focusWorkspaceId={focusWorkspaceId}
        activeWorkspace={activeWorkspace}
        notebooksLoading={notebooksQuery.isLoading}
        newWorkspaceName={newWorkspaceName}
        onWorkspaceNameChange={setNewWorkspaceName}
        onCreateWorkspace={() => createMutation.mutate()}
        createPending={createMutation.isPending}
        createError={createMutation.isError}
        error={createMutation.error}
        onSearch={() => navigate('/app/search')}
        onNotifications={() => navigate('/app/notifications')}
        onNewNotebook={() => focusWorkspaceId && navigate(`/app/workspaces/${focusWorkspaceId}`)}
        onManageWorkspace={() => focusWorkspaceId && navigate(`/app/workspaces/${focusWorkspaceId}`)}
      />
    </ResponsiveContent>
  )
}
