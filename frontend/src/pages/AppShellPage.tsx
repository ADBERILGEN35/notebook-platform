import { Outlet, useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { listWorkspaces } from '../features/workspaces/workspace-api'
import { listNotebooks, createNotebook } from '../features/notebooks/notebook-api'
import { canShowAdminNavigation } from '../features/admin/access/admin-access'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { useAuthStore } from '../features/auth/auth-store'
import { Sidebar } from '../shared/layout/Sidebar'
import { Topbar } from '../shared/layout/Topbar'
import { Modal } from '../shared/components/Modal'
import { Input } from '../shared/components/Input'
import { Button } from '../shared/components/Button'

export function AppShellPage() {
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [openCreateNotebook, setOpenCreateNotebook] = useState(false)
  const [newNotebookName, setNewNotebookName] = useState('')
  const activeWorkspaceId = useWorkspaceStore((state) => state.activeWorkspaceId)
  const setActiveWorkspaceId = useWorkspaceStore((state) => state.setActiveWorkspaceId)
  const user = useAuthStore((state) => state.user)
  const showAdminNav = canShowAdminNavigation(user)

  const workspaceQuery = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => listWorkspaces(0, 20),
  })

  const notebooksQuery = useQuery({
    queryKey: ['notebooks', activeWorkspaceId],
    queryFn: () => listNotebooks(activeWorkspaceId!, 0, 20),
    enabled: Boolean(activeWorkspaceId),
  })

  useEffect(() => {
    if (!activeWorkspaceId && workspaceQuery.data?.items.length) {
      setActiveWorkspaceId(workspaceQuery.data.items[0].id)
    }
  }, [activeWorkspaceId, setActiveWorkspaceId, workspaceQuery.data?.items])

  const createNotebookMutation = useMutation({
    mutationFn: () => createNotebook(activeWorkspaceId!, { name: newNotebookName }),
    onSuccess: (notebook) => {
      setOpenCreateNotebook(false)
      setNewNotebookName('')
      navigate(`/app/notebooks/${notebook.id}`)
    },
  })

  return (
    <div className="flex min-h-screen">
      <Sidebar
        workspaces={workspaceQuery.data?.items || []}
        notebooks={notebooksQuery.data?.items || []}
        activeWorkspaceId={activeWorkspaceId}
        showAdminNav={showAdminNav}
        onWorkspaceSelect={(id) => {
          setActiveWorkspaceId(id)
          navigate(`/app/workspaces/${id}`)
        }}
      />
      <div className="flex min-h-screen flex-1 flex-col">
        <Topbar
          search={search}
          onSearchChange={setSearch}
          onCreateNote={() => setOpenCreateNotebook(true)}
        />
        <main className="flex-1 p-4">
          <Outlet />
        </main>
      </div>
      <Modal open={openCreateNotebook} title="Create notebook" onClose={() => setOpenCreateNotebook(false)}>
        <div className="space-y-2">
          <Input value={newNotebookName} onChange={(event) => setNewNotebookName(event.target.value)} />
          <Button
            className="bg-primary-600 text-white hover:bg-primary-700"
            onClick={() => createNotebookMutation.mutate()}
            disabled={!activeWorkspaceId || !newNotebookName.trim()}
          >
            Create
          </Button>
        </div>
      </Modal>
    </div>
  )
}

