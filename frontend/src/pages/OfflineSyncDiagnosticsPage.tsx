import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { PageHeader } from '../shared/components/PageHeader'
import { SectionCard } from '../shared/components/SectionCard'
import { Button } from '../shared/components/Button'
import { ErrorState } from '../shared/components/ErrorState'
import { InlineStatus } from '../shared/components/InlineStatus'
import { SyncHealthCard } from '../features/sync-diagnostics/SyncHealthCard'
import { LocalDataUsageCard } from '../features/sync-diagnostics/LocalDataUsageCard'
import { ClearLocalDataDialog } from '../features/sync-diagnostics/ClearLocalDataDialog'
import { getOfflineSyncDiagnostics } from '../features/offline/offline-sync-diagnostics'
import { listOfflineNotes, clearOfflineNotes } from '../features/offline/offline-note-cache'
import { listPendingDrafts, listOfflineDraftOverview } from '../features/offline/offline-note-drafts'
import {
  isOfflineEditEnabled,
  isOfflineNotesEnabled,
  isOfflineSyncEnabled,
  offlineBackgroundSyncMode,
  isOfflineBackgroundSyncEnabled,
} from '../shared/config/offline-feature-flags'
import { hasOfflineEncryptionKey } from '../features/offline/offline-crypto'

export function OfflineSyncDiagnosticsPage() {
  const [clearOpen, setClearOpen] = useState(false)
  const offlineNotesEnabled = isOfflineNotesEnabled()
  const diagnosticsQuery = useQuery({
    queryKey: ['offline-sync-diagnostics'],
    queryFn: getOfflineSyncDiagnostics,
    enabled: offlineNotesEnabled,
  })
  const notesCountQuery = useQuery({
    queryKey: ['offline-notes-count'],
    queryFn: async () => (await listOfflineNotes()).length,
    enabled: offlineNotesEnabled,
  })
  const pendingQuery = useQuery({
    queryKey: ['offline-pending-count'],
    queryFn: async () => (await listPendingDrafts()).length,
    enabled: offlineNotesEnabled && isOfflineEditEnabled(),
  })
  const draftsQuery = useQuery({
    queryKey: ['offline-draft-overview'],
    queryFn: listOfflineDraftOverview,
    enabled: offlineNotesEnabled && isOfflineEditEnabled(),
  })

  const clearMutation = useMutation({
    mutationFn: clearOfflineNotes,
    onSuccess: () => {
      setClearOpen(false)
      void notesCountQuery.refetch()
      void pendingQuery.refetch()
      void diagnosticsQuery.refetch()
    },
  })

  const diagnostics = diagnosticsQuery.data

  return (
    <div className="space-y-6">
      <PageHeader title="Offline & sync" subtitle="Diagnostics for cached notes and draft sync." />
      {!offlineNotesEnabled ? (
        <SectionCard title="Offline mode">
          <p className="text-body-md text-on-surface-variant">Offline notes are disabled in this environment.</p>
        </SectionCard>
      ) : (
        <>
          {diagnostics ? (
            <SyncHealthCard
              pending={diagnostics.draftsPending}
              conflict={diagnostics.draftsConflict}
              failed={diagnostics.draftsFailed}
              syncEnabled={isOfflineSyncEnabled()}
              backgroundMode={isOfflineBackgroundSyncEnabled() ? offlineBackgroundSyncMode() : 'disabled'}
            />
          ) : null}
          <LocalDataUsageCard
            cachedNotes={notesCountQuery.data ?? 0}
            pendingDrafts={pendingQuery.data ?? 0}
            encryptionActive={hasOfflineEncryptionKey()}
          />
          {(diagnostics?.draftsConflict ?? 0) > 0 ? (
            <SectionCard title="Sync conflicts">
              <p className="text-body-md text-on-surface-variant">
                Conflicted drafts need resolution in the note editor. Open the note and use the conflict review flow.
              </p>
              <Link to="/app" className="mt-2 inline-block text-primary hover:underline">
                Go to workspace hub
              </Link>
            </SectionCard>
          ) : null}
          <SectionCard title="Pending drafts">
            {draftsQuery.isLoading ? <p className="text-body-md text-on-surface-variant">Loading…</p> : null}
            {draftsQuery.data?.length === 0 ? (
              <p className="text-body-md text-on-surface-variant">No local drafts.</p>
            ) : (
              <ul className="divide-y divide-outline-variant rounded-lg border border-outline-variant">
                {draftsQuery.data?.slice(0, 10).map((draft) => (
                  <li key={draft.noteId} className="flex flex-wrap justify-between gap-2 px-3 py-2 text-body-md">
                    <span>Note {draft.noteId.slice(0, 8)}…</span>
                    <InlineStatus label={draft.status} tone={draft.status.includes('CONFLICT') ? 'warning' : 'neutral'} />
                  </li>
                ))}
              </ul>
            )}
          </SectionCard>
          <SectionCard title="Maintenance">
            <Button type="button" className="bg-error text-white hover:opacity-90" onClick={() => setClearOpen(true)}>
              Clear local offline data
            </Button>
            {clearMutation.isError ? <ErrorState error={clearMutation.error} className="mt-3" /> : null}
          </SectionCard>
        </>
      )}
      <ClearLocalDataDialog
        open={clearOpen}
        loading={clearMutation.isPending}
        onConfirm={() => clearMutation.mutate()}
        onClose={() => setClearOpen(false)}
      />
    </div>
  )
}
