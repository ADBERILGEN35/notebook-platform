import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { createWorkspace, listWorkspaces } from '../features/workspaces/workspace-api'
import { listNotebooks } from '../features/notebooks/notebook-api'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { useAuthStore } from '../features/auth/auth-store'
import { OnboardingWizard } from '../features/onboarding/components/OnboardingWizard'
import { isOnboardingComplete, markOnboardingComplete } from '../features/onboarding/onboarding-storage'
import {
  WorkspaceDashboardEmpty,
  WorkspaceDashboardPopulated,
} from '../features/workspace-dashboard/components'
import { PageHeader } from '../shared/components/PageHeader'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { ErrorState } from '../shared/components/ErrorState'
import { LoadingState } from '../shared/components/LoadingState'
import { Button } from '../shared/components/Button'

export function WorkspaceHubPage() {
  const navigate = useNavigate()
  const params = useParams()
  const routeWorkspaceId = params.workspaceId
  const user = useAuthStore((state) => state.user)
  const activeWorkspaceId = useWorkspaceStore((state) => state.activeWorkspaceId)
  const setActiveWorkspaceId = useWorkspaceStore((state) => state.setActiveWorkspaceId)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')
  const [showOnboarding, setShowOnboarding] = useState(false)
  const [onboardingCreated, setOnboardingCreated] = useState(false)

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
      setOnboardingCreated(true)
      void workspaceQuery.refetch()
      if (!showOnboarding) {
        navigate(`/app/workspaces/${workspace.id}`)
      }
    },
  })

  const workspaces = workspaceQuery.data?.items ?? []

  useEffect(() => {
    if (workspaceQuery.isSuccess && workspaces.length > 0 && !isOnboardingComplete()) {
      markOnboardingComplete()
    }
  }, [workspaceQuery.isSuccess, workspaces.length])

  useEffect(() => {
    if (workspaceQuery.isSuccess && workspaces.length === 0 && !isOnboardingComplete()) {
      setShowOnboarding(true)
    }
  }, [workspaceQuery.isSuccess, workspaces.length])

  if (workspaceQuery.isLoading) {
    return (
      <ResponsiveContent>
        <LoadingState label="Loading workspaces…" />
      </ResponsiveContent>
    )
  }

  if (workspaceQuery.isError) {
    return (
      <ResponsiveContent className="flex min-h-[min(28rem,calc(100dvh-11rem))] flex-col justify-center gap-4">
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

  if (!workspaces.length && showOnboarding) {
    return (
      <ResponsiveContent>
        <PageHeader
          title="Get started"
          subtitle="Complete setup to create your first workspace."
        />
        <OnboardingWizard
          workspaceName={newWorkspaceName}
          onWorkspaceNameChange={setNewWorkspaceName}
          onCreateWorkspace={() => createMutation.mutate()}
          createPending={createMutation.isPending}
          createError={createMutation.isError}
          hasWorkspace={onboardingCreated || workspaces.length > 0}
          onFinish={() => {
            setShowOnboarding(false)
            void workspaceQuery.refetch()
          }}
        />
      </ResponsiveContent>
    )
  }

  if (!workspaces.length) {
    return (
      <ResponsiveContent>
        <WorkspaceDashboardEmpty
          workspaceName={newWorkspaceName}
          onWorkspaceNameChange={setNewWorkspaceName}
          onCreateWorkspace={() => createMutation.mutate()}
          createPending={createMutation.isPending}
          createError={createMutation.isError}
          error={createMutation.error}
          showGuidedSetup={!isOnboardingComplete()}
          onGuidedSetup={() => setShowOnboarding(true)}
          onSearch={() => navigate('/app/search')}
          onNotifications={() => navigate('/app/notifications')}
        />
      </ResponsiveContent>
    )
  }

  const notebooks = notebooksQuery.data?.items ?? []
  const activeWorkspace = workspaces.find((w) => w.id === focusWorkspaceId)

  return (
    <ResponsiveContent>
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
