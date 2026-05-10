import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
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
import { useMediaQuery } from '../shared/hooks/useMediaQuery'
import { MobileSidebar } from '../shared/layout/MobileSidebar'
import { useNotificationEventStream } from '../features/notifications/notification-hooks'
import { useOnlineStatus } from '../shared/hooks/useOnlineStatus'
import {
  isOfflineBackgroundSyncEnabled,
  isOfflineDraftEncryptionRequired,
  isOfflineEncryptionEnabled,
  offlineBackgroundSyncMode,
} from '../shared/config/offline-feature-flags'
import { hasOfflineEncryptionKey } from '../features/offline/offline-crypto'
import { runForegroundBackgroundSync } from '../features/offline/offline-background-sync-service'
import { OfflineSyncPromptBanner } from '../features/offline/components/OfflineSyncPromptBanner'
import { OfflineSyncSummaryBanner } from '../features/offline/components/OfflineSyncSummaryBanner'
import type { BackgroundSyncSummary } from '../features/offline/offline-background-sync-types'

export function AppShellPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [search, setSearch] = useState('')
  const [openCreateNotebook, setOpenCreateNotebook] = useState(false)
  const [newNotebookName, setNewNotebookName] = useState('')
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)
  const activeWorkspaceId = useWorkspaceStore((state) => state.activeWorkspaceId)
  const setActiveWorkspaceId = useWorkspaceStore((state) => state.setActiveWorkspaceId)
  const user = useAuthStore((state) => state.user)
  const accessToken = useAuthStore((state) => state.accessToken)
  const showAdminNav = canShowAdminNavigation(user)
  const isDesktop = useMediaQuery('(min-width: 1024px)')
  const { isOnline } = useOnlineStatus()
  const backgroundSyncEnabled = isOfflineBackgroundSyncEnabled()
  const backgroundMode = offlineBackgroundSyncMode()
  const backgroundSyncVisible = backgroundSyncEnabled && backgroundMode !== 'disabled'
  const backgroundSyncRunningRef = useRef(false)
  const [promptSummary, setPromptSummary] = useState<BackgroundSyncSummary | null>(null)
  const [syncingCount, setSyncingCount] = useState(0)
  const [summaryState, setSummaryState] = useState<'syncing' | 'summary' | 'blocked' | null>(null)
  const [lastSummary, setLastSummary] = useState<BackgroundSyncSummary | null>(null)
  const activeNoteId = useMemo(() => {
    const match = /^\/app\/notes\/([^/]+)/.exec(location.pathname)
    return match?.[1] ?? null
  }, [location.pathname])
  const isSettingsRoute = location.pathname.startsWith('/app/settings')

  const evaluateBackgroundSync = useCallback(
    async (allowPromptExecution = false) => {
      if (!backgroundSyncVisible || backgroundSyncRunningRef.current) return null
      backgroundSyncRunningRef.current = true
      try {
        const encryptionReady =
          !isOfflineEncryptionEnabled() || !isOfflineDraftEncryptionRequired() || hasOfflineEncryptionKey()
        const authenticated = Boolean(user || accessToken)
        const runSummary = await runForegroundBackgroundSync({
          authenticated,
          encryptionReady,
          allowPromptExecution,
          activeNoteId,
        })
        setLastSummary(runSummary)
        if (runSummary.needsUserConsent && runSummary.eligibleCount > 0 && !allowPromptExecution) {
          setPromptSummary(runSummary)
          setSummaryState(null)
          return runSummary
        }
        setPromptSummary(null)
        if (runSummary.attempted > 0) {
          setSummaryState('summary')
        } else if (
          runSummary.stopReason === 'ENCRYPTION_KEY_UNAVAILABLE' ||
          runSummary.stopReason === 'UNAUTHENTICATED'
        ) {
          setSummaryState('blocked')
        } else {
          setSummaryState(null)
        }
        return runSummary
      } finally {
        backgroundSyncRunningRef.current = false
      }
    },
    [accessToken, activeNoteId, backgroundSyncVisible, user],
  )

  useNotificationEventStream()

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

  useEffect(() => {
    setIsSidebarOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (!backgroundSyncVisible || !isOnline) return
    void evaluateBackgroundSync(false)
  }, [backgroundSyncVisible, evaluateBackgroundSync, isOnline, user, accessToken])

  useEffect(() => {
    if (!backgroundSyncVisible) return
    const onOnline = () => {
      void evaluateBackgroundSync(false)
    }
    const onFocus = () => {
      void evaluateBackgroundSync(false)
    }
    window.addEventListener('online', onOnline)
    window.addEventListener('focus', onFocus)
    return () => {
      window.removeEventListener('online', onOnline)
      window.removeEventListener('focus', onFocus)
    }
  }, [backgroundSyncVisible, evaluateBackgroundSync])

  useEffect(() => {
    if (!backgroundSyncVisible || !isSettingsRoute) return
    void evaluateBackgroundSync(false)
  }, [backgroundSyncVisible, evaluateBackgroundSync, isSettingsRoute])

  const createNotebookMutation = useMutation({
    mutationFn: () => createNotebook(activeWorkspaceId!, { name: newNotebookName }),
    onSuccess: (notebook) => {
      setOpenCreateNotebook(false)
      setNewNotebookName('')
      navigate(`/app/notebooks/${notebook.id}`)
    },
  })

  return (
    <div className="flex min-h-screen overflow-x-hidden">
      {isDesktop ? (
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
      ) : null}
      <MobileSidebar
        open={!isDesktop && isSidebarOpen}
        onClose={() => setIsSidebarOpen(false)}
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
          onSidebarToggle={() => setIsSidebarOpen(true)}
          isOnline={isOnline}
        />
        <main className="flex-1 p-3 sm:p-4">
          {promptSummary ? (
            <OfflineSyncPromptBanner
              readyCount={promptSummary.eligibleCount}
              onSyncNow={() => {
                setPromptSummary(null)
                setSummaryState('syncing')
                setSyncingCount(promptSummary.eligibleCount)
                void evaluateBackgroundSync(true).then(() => setSyncingCount(0))
              }}
              onReviewDrafts={() => {
                setPromptSummary(null)
                navigate('/app/settings/security')
              }}
              onNotNow={() => setPromptSummary(null)}
            />
          ) : null}
          {summaryState ? (
            <OfflineSyncSummaryBanner state={summaryState} syncingCount={syncingCount} summary={lastSummary} />
          ) : null}
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

