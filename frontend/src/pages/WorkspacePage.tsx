import { useMutation, useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { createWorkspace, listWorkspaces } from '../features/workspaces/workspace-api'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { EmptyState } from '../shared/components/EmptyState'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { LoadingState } from '../shared/components/LoadingState'
import { Button } from '../shared/components/Button'
import { Input } from '../shared/components/Input'
import { useState } from 'react'
import { PageHeader } from '../shared/components/PageHeader'

export function WorkspacePage() {
  const params = useParams()
  const activeWorkspaceId = useWorkspaceStore((state) => state.activeWorkspaceId)
  const setActiveWorkspaceId = useWorkspaceStore((state) => state.setActiveWorkspaceId)
  const [name, setName] = useState('')

  const query = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => listWorkspaces(0, 20),
  })

  const createMutation = useMutation({
    mutationFn: () => createWorkspace({ name, type: 'TEAM' }),
    onSuccess: (workspace) => {
      setName('')
      setActiveWorkspaceId(workspace.id)
    },
  })

  if (query.isLoading) return <LoadingState />
  if (query.isError) return <ErrorAlert error={query.error} />

  if (!query.data?.items.length) {
    return <EmptyState title="No workspace yet" message="Create your first workspace to continue." />
  }

  return (
    <div className="space-y-3">
      <PageHeader
        title="Workspace"
        subtitle={`Active workspace: ${params.workspaceId || activeWorkspaceId || 'none'}`}
      />
      <div className="max-w-md space-y-2">
        <Input
          placeholder="Create new workspace"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
        <Button onClick={() => createMutation.mutate()} disabled={!name.trim()}>
          Create workspace
        </Button>
      </div>
    </div>
  )
}

