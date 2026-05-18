import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { listComments, createComment, resolveComment, reopenComment } from '../features/comments/comments-api'
import { useNoteAutoSave } from '../features/notes/hooks/useNoteAutoSave'
import { BlockNoteEditor } from '../features/notes/components/BlockNoteEditor'
import {
  applyMerge,
  analyzeMerge,
  createNote,
  getNote,
  type NoteWithEtag,
  updateNote,
} from '../features/notes/note-api'
import {
  createEmptyDocument,
  toBlockNoteDocument,
} from '../features/notes/utils/blocknote-serialization'
import { listVersions, restoreVersion } from '../features/versions/versions-api'
import { Button } from '../shared/components/Button'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { Input } from '../shared/components/Input'
import { LoadingState } from '../shared/components/LoadingState'
import { EmptyState } from '../shared/components/EmptyState'
import { ApiError } from '../shared/api/api-client'
import { AccessDeniedState } from '../features/access/components/AccessDeniedState'
import { AccessRequestPanel } from '../features/access/components/AccessRequestPanel'
import { trackMergeEvent } from '../features/notes/merge-analytics'
import { RightPanel } from '../shared/layout/RightPanel'
import type { NoteBlock } from '../shared/types/api'
import { useEffect, useMemo, useState } from 'react'
import { useMediaQuery } from '../shared/hooks/useMediaQuery'
import { RightPanelDrawer } from '../shared/layout/RightPanelDrawer'
import { extractPlainTextFromBlocks } from '../features/notes/utils/blocknote-serialization'
import { createNoteSaveSnapshot } from '../features/notes/utils/note-save-snapshot'
import {
  analyzeNoteConflict,
  type MergeAnalysis,
  type MergeConflictReason,
} from '../features/notes/utils/blocknote-merge'
import { NoteConflictResolutionDialog } from '../features/notes/components/NoteConflictResolutionDialog'
import { useOnlineStatus } from '../shared/hooks/useOnlineStatus'
import { getOfflineNote, saveOfflineNote } from '../features/offline/offline-note-cache'
import {
  isOfflineDraftEncryptionRequired,
  isOfflineEditEnabled,
  isOfflineNotesEnabled,
  isOfflineSyncEnabled,
  isBackendMergeApplyEnabled,
  isBackendMergeAnalysisEnabled,
  offlineSyncRolloutMode,
} from '../shared/config/offline-feature-flags'
import {
  deleteOfflineDraft,
  getOfflineDraft,
  markDraftQueued,
  saveOfflineDraft,
} from '../features/offline/offline-note-drafts'
import {
  getOfflineDraftConflictData,
  resolveDraftConflictOverwrite,
  resolveDraftConflictSaveAsCopy,
  resolveDraftConflictSuggestedMerge,
  syncOfflineDraft,
} from '../features/offline/offline-sync-service'
import {
  ensureOfflineEncryptionKey,
  hasOfflineEncryptionKey,
  isOfflineCryptoSupported,
} from '../features/offline/offline-crypto'

type RightTab = 'comments' | 'versions' | 'info'
const AUTO_SAVE_ENABLED = true
const AUTO_SAVE_DEBOUNCE_MS = 1500
const AUTO_SAVE_MIN_CHANGE_INTERVAL_MS = 1000
const OFFLINE_DRAFT_SAVE_DEBOUNCE_MS = 750

type NotePageProps = {
  embedded?: boolean
  expectedWorkspaceId?: string
}

export function NotePage({ embedded, expectedWorkspaceId }: NotePageProps = {}) {
  const { noteId } = useParams()
  const [accessRequestSent, setAccessRequestSent] = useState(false)
  const navigate = useNavigate()
  const [tab, setTab] = useState<RightTab>('comments')
  const [title, setTitle] = useState('')
  const [contentBlocks, setContentBlocks] = useState<NoteBlock[]>(createEmptyDocument())
  const [commentInput, setCommentInput] = useState('')
  const [contentParseError, setContentParseError] = useState<string | null>(null)
  const [hydratedNoteSignature, setHydratedNoteSignature] = useState<string | null>(null)
  const [isRightPanelOpen, setIsRightPanelOpen] = useState(false)
  const [isConflictDialogOpen, setIsConflictDialogOpen] = useState(false)
  const [offlineDraftStatus, setOfflineDraftStatus] = useState('Saved')
  const [hasDraftPrompt, setHasDraftPrompt] = useState(false)
  const [activeOfflineDraftNoteId, setActiveOfflineDraftNoteId] = useState<string | null>(null)
  const [draftConflictNoteId, setDraftConflictNoteId] = useState<string | null>(null)
  const isDesktop = useMediaQuery('(min-width: 1024px)')
  const { isOnline } = useOnlineStatus()
  const offlineEnabled = isOfflineNotesEnabled()
  const backendMergeAnalysisEnabled = isBackendMergeAnalysisEnabled()
  const backendMergeApplyEnabled = isBackendMergeApplyEnabled()
  const offlineEditEnabled = isOfflineEditEnabled()
  const offlineSyncEnabled = isOfflineSyncEnabled()
  const syncRolloutMode = offlineSyncRolloutMode()
  const offlineDraftEncryptionRequired = isOfflineDraftEncryptionRequired()
  const offlineEditSecurityBlocked =
    offlineEditEnabled &&
    offlineDraftEncryptionRequired &&
    (!isOfflineCryptoSupported() || !hasOfflineEncryptionKey())

  useEffect(() => {
    if (offlineEditEnabled && offlineDraftEncryptionRequired) {
      void ensureOfflineEncryptionKey()
    }
  }, [offlineDraftEncryptionRequired, offlineEditEnabled])

  const noteQuery = useQuery({
    queryKey: ['note', noteId],
    queryFn: async () => {
      try {
        const result = await getNote(noteId!)
        if (offlineEnabled) {
          await saveOfflineNote(result.note, result.etag)
        }
        return { ...result, source: 'network' as const }
      } catch (error) {
        if (offlineEnabled && (!isOnline || isNetworkError(error))) {
          const cached = await getOfflineNote(noteId!)
          if (cached) {
            return { note: cached.note, etag: cached.etag, source: 'offline' as const }
          }
          throw new Error('OFFLINE_NOTE_UNAVAILABLE')
        }
        throw error
      }
    },
    enabled: Boolean(noteId),
  })
  const isOfflineSource = noteQuery.data?.source === 'offline'
  const isOfflineReadOnly = isOfflineSource && (!offlineEditEnabled || offlineEditSecurityBlocked)
  const canEditOfflineDraft = isOfflineSource && offlineEditEnabled && !offlineEditSecurityBlocked
  const commentsQuery = useQuery({
    queryKey: ['comments', noteId],
    queryFn: () => listComments(noteId!, 0, 20),
    enabled: Boolean(noteId) && isOnline && !isOfflineSource,
  })
  const versionsQuery = useQuery({
    queryKey: ['versions', noteId],
    queryFn: () => listVersions(noteId!, 0, 20),
    enabled: Boolean(noteId) && isOnline && !isOfflineSource,
  })

  const saveMutation = useMutation({
    mutationFn: (payload: { title: string; contentBlocks: NoteBlock[]; ifMatch: string | null }) =>
      updateNote(noteId!, payload, payload.ifMatch),
    onSuccess: (savedNote) => {
      noteQuery.refetch()
      versionsQuery.refetch()
      if (offlineEnabled) {
        void saveOfflineNote(savedNote.note, savedNote.etag)
      }
    },
  })
  const addCommentMutation = useMutation({
    mutationFn: () => createComment(noteId!, { content: commentInput }),
    onSuccess: () => setCommentInput(''),
  })
  const resolveMutation = useMutation({ mutationFn: resolveComment })
  const reopenMutation = useMutation({ mutationFn: reopenComment })
  const restoreMutation = useMutation<NoteWithEtag, unknown, number>({
    mutationFn: (version: number) => restoreVersion(noteId!, version, autoSave.etag),
    onSuccess: (restoredNote) => {
      setTitle(restoredNote.note.title)
      setContentBlocks(restoredNote.note.contentBlocks)
      autoSave.resetWithServerVersion(restoredNote.note, restoredNote.etag)
      noteQuery.refetch()
      versionsQuery.refetch()
      if (offlineEnabled) {
        void saveOfflineNote(restoredNote.note, restoredNote.etag)
      }
    },
  })
  const copyMutation = useMutation({
    mutationFn: (payload: { notebookId: string; title: string; contentBlocks: NoteBlock[] }) =>
      createNote(payload.notebookId, { title: payload.title, contentBlocks: payload.contentBlocks }),
  })

  const autoSave = useNoteAutoSave({
    noteId: noteId ?? '',
    title,
    contentBlocks,
    enabled: AUTO_SAVE_ENABLED && !isOfflineSource,
    debounceMs: AUTO_SAVE_DEBOUNCE_MS,
    minChangeIntervalMs: AUTO_SAVE_MIN_CHANGE_INTERVAL_MS,
    baseUpdatedAt: noteQuery.data?.note.updatedAt ?? null,
    initialEtag: noteQuery.data?.etag ?? null,
    saveNote: async (payload) => saveMutation.mutateAsync(payload),
  })

  const note = noteQuery.data?.note
  const noteEtag = noteQuery.data?.etag ?? null
  const conflictInfo = autoSave.conflictInfo
  const draftConflictQuery = useQuery({
    queryKey: ['note', noteId, 'offline-draft-conflict'],
    queryFn: () => getOfflineDraftConflictData(draftConflictNoteId!),
    enabled: Boolean(draftConflictNoteId),
  })
  const conflictRemoteQuery = useQuery({
    queryKey: ['note', noteId, 'conflict-remote'],
    queryFn: () => getNote(noteId!),
    enabled: Boolean(noteId && isConflictDialogOpen && conflictInfo && !isOfflineReadOnly),
  })
  const backendMergeQuery = useQuery({
    queryKey: ['note', noteId, 'conflict-backend-merge', conflictInfo?.failedAt],
    queryFn: () =>
      analyzeMerge(noteId!, {
        base: {
          ...conflictInfo!.baseSnapshot,
          etag: conflictInfo?.serverEtagAtFailure ?? noteEtag ?? null,
        },
        local: conflictInfo!.localSnapshot,
        clientMergeVersion: 1,
      }),
    enabled: Boolean(
      noteId && isConflictDialogOpen && conflictInfo && !isOfflineReadOnly && backendMergeAnalysisEnabled,
    ),
    retry: 0,
  })
  const backendToClientReason = (type: string): MergeConflictReason => {
    switch (type) {
      case 'SAME_BLOCK_CHANGED':
        return 'same_block_divergent'
      case 'DELETE_VS_EDIT':
        return 'delete_vs_edit'
      case 'UNKNOWN_BLOCK_TYPE':
        return 'unknown_block_type'
      case 'MISSING_BLOCK_ID':
        return 'missing_block_id'
      case 'DUPLICATE_BLOCK_ID':
        return 'duplicate_block_id'
      case 'TITLE_DIVERGENT':
        return 'title_divergent'
      case 'BLOCK_MOVE_CONFLICT':
        return 'block_move_conflict'
      case 'BLOCK_MOVED_AND_EDITED':
        return 'block_moved_and_edited'
      case 'BLOCK_DELETED_AFTER_MOVE':
        return 'block_deleted_after_move'
      case 'BLOCK_CROSS_PARENT_UNSUPPORTED':
        return 'block_cross_parent_unsupported'
      default:
        return 'move_or_structure'
    }
  }
  const serverMergeAnalysis: MergeAnalysis | null = useMemo(() => {
    if (!backendMergeQuery.data) return null
    const moveConflicts = backendMergeQuery.data.conflicts
      .filter((conflict) => conflict.type === 'BLOCK_MOVE_CONFLICT')
      .map((conflict) => ({
        blockId: conflict.blockId ?? '',
        blockType: 'block',
        fromIndex: null,
        toIndex: null,
        parentChanged: false,
      }))
    return {
      suggestion: backendMergeQuery.data.suggested,
      conflicts: backendMergeQuery.data.conflicts.map((conflict) => ({
        reason: backendToClientReason(conflict.type),
        blockId: conflict.blockId ?? undefined,
        message: conflict.message,
      })),
      localChangeSummary: backendMergeQuery.data.summary.localChanges,
      remoteChangeSummary: backendMergeQuery.data.summary.remoteChanges,
      conflictSummaries: backendMergeQuery.data.summary.conflicts,
      movedBlocks: [],
      reorderedBlocks: [],
      moveConflicts,
    }
  }, [backendMergeQuery.data])
  const mergeAnalysis = useMemo(() => {
    if (serverMergeAnalysis) return serverMergeAnalysis
    if (!conflictInfo || !conflictRemoteQuery.data) return null
    return analyzeNoteConflict(
      conflictInfo.baseSnapshot,
      conflictInfo.localSnapshot,
      createNoteSaveSnapshot(
        conflictRemoteQuery.data.note.title,
        conflictRemoteQuery.data.note.contentBlocks,
      ),
    )
  }, [conflictInfo, conflictRemoteQuery.data, serverMergeAnalysis])
  const trackConflictAction = (action: 'apply_merge' | 'reload_latest' | 'save_copy' | 'overwrite_latest' | 'cancel' | 'backend_analyze_fallback') => {
    const effectiveAnalysis = draftConflictQuery.data?.analysis ?? mergeAnalysis
    trackMergeEvent({
      source: draftConflictNoteId ? 'offline_draft' : 'online',
      backendAnalyzeUsed: Boolean(backendMergeQuery.data),
      backendApplyUsed: backendMergeApplyEnabled,
      action,
      hasSafeSuggestion: Boolean(effectiveAnalysis?.suggestion),
      conflictCount: effectiveAnalysis?.conflicts.length ?? 0,
    })
  }
  const localConflictPreview = extractPlainTextFromBlocks(conflictInfo?.localSnapshot.contentBlocks ?? [])
  const serverPreview = extractPlainTextFromBlocks(
    conflictRemoteQuery.data?.note.contentBlocks ?? note?.contentBlocks ?? [],
  )
  const serverTitleForDialog = conflictRemoteQuery.data?.note.title ?? note?.title ?? ''
  useEffect(() => {
    if (!note) return
    const signature = `${note.id}:${note.updatedAt}`
    if (hydratedNoteSignature === signature) return
    setTitle(note.title)
    try {
      const safeBlocks = toBlockNoteDocument(note.contentBlocks) as unknown as NoteBlock[]
      setContentBlocks(safeBlocks.length ? safeBlocks : createEmptyDocument())
      setContentParseError(null)
      setHydratedNoteSignature(signature)
      autoSave.resetWithServerVersion(note, noteEtag)
    } catch {
      setContentBlocks(createEmptyDocument())
      setContentParseError('Content could not be rendered safely. A safe fallback document was loaded.')
      setHydratedNoteSignature(signature)
      autoSave.resetWithServerVersion({
        ...note,
        contentBlocks: createEmptyDocument(),
      }, noteEtag)
    }
  }, [autoSave, hydratedNoteSignature, note, noteEtag])

  useEffect(() => {
    if (!isConflictDialogOpen) return
    const effectiveAnalysis = draftConflictQuery.data?.analysis ?? mergeAnalysis
    trackMergeEvent({
      source: draftConflictNoteId ? 'offline_draft' : 'online',
      backendAnalyzeUsed: Boolean(backendMergeQuery.data),
      backendApplyUsed: backendMergeApplyEnabled,
      action: 'dialog_opened',
      hasSafeSuggestion: Boolean(effectiveAnalysis?.suggestion),
      conflictCount: effectiveAnalysis?.conflicts.length ?? 0,
    })
  }, [
    backendMergeApplyEnabled,
    backendMergeQuery.data,
    draftConflictNoteId,
    draftConflictQuery.data,
    isConflictDialogOpen,
    mergeAnalysis,
  ])

  useEffect(() => {
    if (!noteId || !offlineEditEnabled) return
    let cancelled = false
    void getOfflineDraft(noteId).then((draft) => {
      if (cancelled || !draft) return
      if (['DRAFT', 'QUEUED', 'CONFLICT'].includes(draft.status)) {
        setHasDraftPrompt(true)
        setActiveOfflineDraftNoteId(draft.noteId)
          if (draft.status === 'QUEUED') setOfflineDraftStatus('Queued for sync')
          if (draft.status === 'CONFLICT') setOfflineDraftStatus('This draft has a conflict')
        if (draft.status === 'CONFLICT') {
          setDraftConflictNoteId(draft.noteId)
        }
      }
    })
    return () => {
      cancelled = true
    }
  }, [noteId, offlineEditEnabled])

  useEffect(() => {
    if (!note || !noteId || !offlineEditEnabled || !canEditOfflineDraft) return
    const timer = window.setTimeout(() => {
      void saveOfflineDraft({
        noteId,
        workspaceId: note.workspaceId,
        notebookId: note.notebookId,
        baseEtag: noteEtag,
        baseUpdatedAt: note.updatedAt,
        baseSnapshot: { title: note.title, contentBlocks: note.contentBlocks },
        localSnapshot: { title, contentBlocks },
      }).then((saved) => {
        if (saved) {
          setOfflineDraftStatus(saved.status === 'QUEUED' ? 'Queued for sync' : 'Saved offline')
          setActiveOfflineDraftNoteId(saved.noteId)
        }
      })
    }, OFFLINE_DRAFT_SAVE_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [canEditOfflineDraft, contentBlocks, note, note?.notebookId, note?.workspaceId, note?.updatedAt, noteEtag, noteId, offlineEditEnabled, title])

  const saveStatusLabel = useMemo(() => {
    if (canEditOfflineDraft) {
      return offlineDraftStatus
    }
    switch (autoSave.saveState) {
      case 'saving':
        return 'Saving...'
      case 'error':
        return 'Save failed. Retry'
      case 'conflict':
        return 'Conflict detected'
      case 'dirty':
      case 'scheduled':
        return 'Unsaved changes'
      case 'saved':
      case 'idle':
      default:
        return 'Saved'
    }
  }, [autoSave.saveState, canEditOfflineDraft, offlineDraftStatus])

  if (noteQuery.isLoading) return <LoadingState />
  if (noteQuery.isError && (noteQuery.error as Error).message === 'OFFLINE_NOTE_UNAVAILABLE') {
    return (
      <div className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800">
        This note is not available offline.
      </div>
    )
  }
  if (noteQuery.isError) {
    const err = noteQuery.error
    if (err instanceof ApiError && err.status === 403) {
      return (
        <div className="space-y-4">
          <AccessDeniedState
            message="You do not have permission to open this note. Request access from a workspace admin or check your membership."
            onRequestAccess={() => setAccessRequestSent(true)}
            requestPending={accessRequestSent}
          />
          {accessRequestSent ? <AccessRequestPanel status="sent" resourceLabel="This note" /> : null}
        </div>
      )
    }
    if (err instanceof ApiError && err.status === 404) {
      return <EmptyState title="Note not found" message="This note may have been deleted or moved." />
    }
    return <ErrorAlert error={noteQuery.error} />
  }

  const loadedNote = noteQuery.data?.note
  if (expectedWorkspaceId && loadedNote && loadedNote.workspaceId !== expectedWorkspaceId) {
    return (
      <AccessDeniedState
        title="Workspace mismatch"
        message="This note does not belong to the workspace in the URL. Open it from the correct workspace hub."
      />
    )
  }

  const editorSurfaceClass = embedded
    ? 'min-w-0 flex-1 rounded-xl border border-outline-variant bg-surface-container-lowest p-3 sm:p-4'
    : 'min-w-0 flex-1 rounded-lg border border-slate-200 bg-white p-3 sm:p-4'

  return (
    <>
    <div className={`flex h-full flex-col gap-3 ${embedded ? '' : 'lg:flex-row'}`}>
      <section className={editorSurfaceClass}>
        {isOfflineReadOnly ? (
          <div className="mb-2 rounded border border-amber-300 bg-amber-50 p-2 text-sm text-amber-800">
            Offline copy loaded. Editing is disabled while offline.
          </div>
        ) : null}
        {canEditOfflineDraft ? (
          <div className="mb-2 rounded border border-emerald-300 bg-emerald-50 p-2 text-sm text-emerald-800">
            You are offline. Changes are stored locally as an offline draft.
          </div>
        ) : null}
        {offlineEditSecurityBlocked ? (
          <div className="mb-2 rounded border border-amber-300 bg-amber-50 p-2 text-sm text-amber-800">
            Offline editing is disabled because encrypted local storage is unavailable.
          </div>
        ) : null}
        {offlineDraftStatus === 'Queued for sync' ? (
          <div className="mb-2 rounded border border-blue-300 bg-blue-50 p-2 text-sm text-blue-800">
            This draft is queued for sync.
          </div>
        ) : null}
        {offlineDraftStatus.includes('conflict') || offlineDraftStatus.includes('Conflict') ? (
          <div className="mb-2 rounded border border-amber-300 bg-amber-50 p-2 text-sm text-amber-800">
            This draft has a conflict and requires manual resolution before syncing again.
          </div>
        ) : null}
        {hasDraftPrompt && activeOfflineDraftNoteId ? (
          <div className="mb-2 rounded border border-indigo-300 bg-indigo-50 p-2 text-sm text-indigo-800">
            You have an offline draft for this note.
            <div className="mt-2 flex flex-wrap gap-2">
              <Button
                type="button"
                onClick={async () => {
                  const draft = await getOfflineDraft(activeOfflineDraftNoteId)
                  if (!draft) return
                  setTitle(draft.localSnapshot.title)
                  setContentBlocks(draft.localSnapshot.contentBlocks)
                  setOfflineDraftStatus(draft.status === 'QUEUED' ? 'Queued for sync' : 'Offline draft')
                  setHasDraftPrompt(false)
                }}
              >
                Continue draft
              </Button>
              <Button
                type="button"
                onClick={async () => {
                  await deleteOfflineDraft(activeOfflineDraftNoteId)
                  setHasDraftPrompt(false)
                  setDraftConflictNoteId(null)
                }}
              >
                Discard draft
              </Button>
              <Button type="button" onClick={() => setHasDraftPrompt(false)}>
                View server version
              </Button>
            </div>
          </div>
        ) : null}
        {contentParseError ? <ErrorAlert error={new Error(contentParseError)} /> : null}
        <Input
          data-testid="note-title-input"
          value={title}
          onChange={(event) => {
            setTitle(event.target.value)
            if (!isOfflineReadOnly) {
              autoSave.scheduleSave()
            }
          }}
          disabled={isOfflineSource}
          className="mb-2 text-lg sm:text-xl"
        />
        <BlockNoteEditor
          initialContentBlocks={contentBlocks}
          readOnly={isOfflineReadOnly}
          isSaving={autoSave.saveState === 'saving'}
          onChange={(blocks) => {
            setContentBlocks(blocks)
            if (!isOfflineReadOnly) {
              autoSave.scheduleSave()
            }
          }}
        />
        {autoSave.saveState === 'conflict' ? (
          <div
            data-testid="conflict-banner"
            className="mt-2 rounded border border-amber-300 bg-amber-50 p-2 text-sm text-amber-800"
          >
            This note was changed elsewhere. Reload the latest version before continuing.
            <button
              className="ml-2 underline"
              data-testid="conflict-review-button"
              onClick={async () => {
                await noteQuery.refetch()
                setIsConflictDialogOpen(true)
              }}
            >
              Review options
            </button>
          </div>
        ) : null}
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
          <p className="text-xs text-slate-500">
            Updated: {note?.updatedAt ? new Date(note.updatedAt).toLocaleString() : '-'}
          </p>
          <div className="flex flex-wrap items-center gap-2 sm:gap-3">
            <span data-testid="save-status" className="text-xs text-slate-500">
              {saveStatusLabel}
            </span>
            <span data-testid="autosave-indicator" className="text-xs text-slate-400">
              Auto-save {AUTO_SAVE_ENABLED && !isOfflineSource ? 'on' : 'off'} ({AUTO_SAVE_DEBOUNCE_MS}ms)
            </span>
            <Button
              data-testid="note-save-button"
              className="bg-primary-600 text-white hover:bg-primary-700"
              onClick={async () => {
                if (canEditOfflineDraft && note && noteId) {
                  const saved = await saveOfflineDraft({
                    noteId,
                    workspaceId: note.workspaceId,
                    notebookId: note.notebookId,
                    baseEtag: noteEtag,
                    baseUpdatedAt: note.updatedAt,
                    baseSnapshot: { title: note.title, contentBlocks: note.contentBlocks },
                    localSnapshot: { title, contentBlocks },
                  })
                  if (saved) setOfflineDraftStatus('Saved offline')
                  return
                }
                await autoSave.saveNow()
              }}
              disabled={autoSave.saveState === 'saving' || isOfflineReadOnly}
            >
              {canEditOfflineDraft ? 'Save offline draft' : 'Save now'}
            </Button>
            {offlineEditEnabled && noteId ? (
              <Button
                type="button"
                onClick={async () => {
                  await markDraftQueued(noteId)
                  setOfflineDraftStatus('Queued for sync')
                }}
                disabled={!canEditOfflineDraft}
              >
                Queue for sync
              </Button>
            ) : null}
            {offlineEditEnabled && noteId ? (
              <Button
                type="button"
                onClick={async () => {
                  const result = await syncOfflineDraft(noteId)
                  if (result.status === 'synced') {
                    setOfflineDraftStatus('Synced')
                    setDraftConflictNoteId(null)
                    void noteQuery.refetch()
                  } else if (result.status === 'conflict') {
                    setOfflineDraftStatus('Sync conflict')
                    setDraftConflictNoteId(noteId)
                    setIsConflictDialogOpen(true)
                    } else if (result.status === 'locked') {
                      setOfflineDraftStatus('This draft cannot be decrypted in this session')
                  } else if (result.status === 'queued') {
                    setOfflineDraftStatus('Queued for sync')
                  } else {
                    setOfflineDraftStatus('Sync failed')
                  }
                }}
                disabled={!isOnline || !offlineSyncEnabled || syncRolloutMode === 'disabled'}
              >
                Sync now
              </Button>
            ) : null}
            {!isDesktop ? (
              <Button
                type="button"
                data-testid="right-panel-toggle"
                onClick={() => setIsRightPanelOpen(true)}
                aria-label="Open details panel"
              >
                Details
              </Button>
            ) : null}
          </div>
        </div>
        <div className="mt-2 text-xs text-slate-400">Manual save and auto-save both use If-Match concurrency.</div>
        {autoSave.saveState === 'error' ? (
          <div className="mt-2">
            <ErrorAlert error={autoSave.error} />
            <Button
              data-testid="save-retry-button"
              className="mt-2"
              onClick={() => autoSave.retry()}
            >
              Retry save
            </Button>
          </div>
        ) : null}
        {copyMutation.isError ? <ErrorAlert error={copyMutation.error} /> : null}
      </section>
      {isDesktop ? (
        <RightPanel
          activeTab={tab}
          onTabChange={setTab}
          comments={commentsQuery.data?.items || []}
          versions={versionsQuery.data?.items || []}
          commentInput={commentInput}
          onCommentInputChange={setCommentInput}
          onAddComment={() => addCommentMutation.mutate()}
          onResolveComment={(commentId) => resolveMutation.mutate(commentId)}
          onReopenComment={(commentId) => reopenMutation.mutate(commentId)}
          onRestoreVersion={(version) => restoreMutation.mutate(version)}
          disabled={isOfflineReadOnly}
        />
      ) : (
        <RightPanelDrawer open={isRightPanelOpen} onClose={() => setIsRightPanelOpen(false)}>
          <RightPanel
            activeTab={tab}
            onTabChange={setTab}
            comments={commentsQuery.data?.items || []}
            versions={versionsQuery.data?.items || []}
            commentInput={commentInput}
            onCommentInputChange={setCommentInput}
            onAddComment={() => addCommentMutation.mutate()}
            onResolveComment={(commentId) => resolveMutation.mutate(commentId)}
            onReopenComment={(commentId) => reopenMutation.mutate(commentId)}
            onRestoreVersion={(version) => restoreMutation.mutate(version)}
            compact
            disabled={isOfflineSource}
          />
        </RightPanelDrawer>
      )}
    </div>
    {isConflictDialogOpen && draftConflictQuery.isError ? (
      <div className="mb-2">
        <ErrorAlert error={new Error('Latest server version is temporarily unavailable. Try again.')} />
      </div>
    ) : null}
    <NoteConflictResolutionDialog
      open={isConflictDialogOpen && (Boolean(conflictInfo) || Boolean(draftConflictQuery.data))}
      onClose={() => {
        trackConflictAction('cancel')
        setIsConflictDialogOpen(false)
      }}
      localSnapshot={
        conflictInfo?.localSnapshot ??
        (draftConflictQuery.data
          ? createNoteSaveSnapshot(
              draftConflictQuery.data.localSnapshot.title,
              draftConflictQuery.data.localSnapshot.contentBlocks,
            )
          : { title: '', contentBlocks: createEmptyDocument(), serialized: '' })
      }
      serverTitle={draftConflictQuery.data?.remote.note.title ?? serverTitleForDialog}
      serverPreview={
        draftConflictQuery.data
          ? extractPlainTextFromBlocks(draftConflictQuery.data.remote.note.contentBlocks)
          : serverPreview
      }
      localPreview={
        draftConflictQuery.data
          ? extractPlainTextFromBlocks(draftConflictQuery.data.localSnapshot.contentBlocks)
          : localConflictPreview
      }
      canSaveCopy={Boolean(note?.notebookId || draftConflictQuery.data)}
      mergeAnalysis={draftConflictQuery.data?.analysis ?? mergeAnalysis}
      remoteLoading={conflictRemoteQuery.isFetching || draftConflictQuery.isFetching}
      onApplySuggestedMerge={async () => {
        trackConflictAction('apply_merge')
        if (draftConflictNoteId) {
          const result = await resolveDraftConflictSuggestedMerge(draftConflictNoteId)
          if (result.status === 'synced') {
            setDraftConflictNoteId(null)
            setOfflineDraftStatus('Synced')
            setIsConflictDialogOpen(false)
            void noteQuery.refetch()
          }
          return
        }
        if (!mergeAnalysis?.suggestion || !conflictRemoteQuery.data || !noteId) return
        if (backendMergeApplyEnabled && conflictInfo) {
          try {
            const applied = await applyMerge(noteId, {
              base: {
                ...conflictInfo.baseSnapshot,
                etag: conflictInfo.serverEtagAtFailure ?? noteEtag ?? null,
              },
              local: conflictInfo.localSnapshot,
              expectedRemoteEtag:
                backendMergeQuery.data?.remoteEtag ?? conflictRemoteQuery.data.etag ?? '',
              mergeVersion: backendMergeQuery.data?.mergeVersion ?? 1,
              idempotencyKey: window.crypto?.randomUUID?.(),
            })
            setTitle(applied.title)
            setContentBlocks(applied.contentBlocks)
            autoSave.resetWithServerVersion(
              {
                ...(note ?? conflictRemoteQuery.data.note),
                title: applied.title,
                contentBlocks: applied.contentBlocks,
              },
              applied.etag,
            )
            setIsConflictDialogOpen(false)
            void noteQuery.refetch()
            return
          } catch (error) {
            if (
              error instanceof ApiError &&
              (error.errorCode === 'NOTE_MERGE_REMOTE_CHANGED' || error.errorCode === 'NOTE_MERGE_CONFLICTS')
            ) {
              trackConflictAction('backend_analyze_fallback')
              await conflictRemoteQuery.refetch()
              await backendMergeQuery.refetch()
              return
            }
            // fall back to existing client-side save path on apply endpoint errors
          }
        }
        const merged = createNoteSaveSnapshot(
          mergeAnalysis.suggestion.title,
          mergeAnalysis.suggestion.contentBlocks,
        )
        const remoteSnap = createNoteSaveSnapshot(
          conflictRemoteQuery.data.note.title,
          conflictRemoteQuery.data.note.contentBlocks,
        )
        await autoSave.saveMergedAfterConflict(merged, remoteSnap, conflictRemoteQuery.data.etag)
        setTitle(mergeAnalysis.suggestion.title)
        setContentBlocks(mergeAnalysis.suggestion.contentBlocks)
        setIsConflictDialogOpen(false)
        void noteQuery.refetch()
      }}
      onReloadLatest={async () => {
        trackConflictAction('reload_latest')
        if (draftConflictNoteId) {
          setIsConflictDialogOpen(false)
          return
        }
        const confirmed = window.confirm('Reload latest and discard local unsaved changes?')
        if (!confirmed) return
        await noteQuery.refetch()
        autoSave.clearConflict()
        setIsConflictDialogOpen(false)
      }}
      onSaveCopy={async () => {
        trackConflictAction('save_copy')
        if (draftConflictNoteId) {
          const created = await resolveDraftConflictSaveAsCopy(draftConflictNoteId)
          if (created) {
            setDraftConflictNoteId(null)
            setIsConflictDialogOpen(false)
            navigate(`/app/notes/${created.noteId}`)
          }
          return
        }
        if (!note?.notebookId || !conflictInfo) return
        const newTitle = `${conflictInfo.localSnapshot.title || note.title} (conflict copy)`
        const created = await copyMutation.mutateAsync({
          notebookId: note.notebookId,
          title: newTitle,
          contentBlocks: conflictInfo.localSnapshot.contentBlocks,
        })
        autoSave.clearConflict()
        setIsConflictDialogOpen(false)
        navigate(`/app/notes/${created.id}`)
      }}
      onOverwrite={async () => {
        trackConflictAction('overwrite_latest')
        if (draftConflictNoteId) {
          const confirmed = window.confirm(
            'Overwrite the latest server version with your offline draft? Previous versions remain in history.',
          )
          if (!confirmed) return
          const result = await resolveDraftConflictOverwrite(draftConflictNoteId)
          if (result.status === 'synced') {
            setDraftConflictNoteId(null)
            setOfflineDraftStatus('Synced')
            setIsConflictDialogOpen(false)
            void noteQuery.refetch()
          }
          return
        }
        if (isOfflineReadOnly) return
        if (!conflictInfo || !noteId) return
        const confirmed = window.confirm(
          'Overwrite the latest server version with your local changes? Previous versions remain in history.'
        )
        if (!confirmed) return
        try {
          const latest = await getNote(noteId)
          const overwritten = await updateNote(
            noteId,
            {
              title: conflictInfo.localSnapshot.title,
              contentBlocks: conflictInfo.localSnapshot.contentBlocks,
            },
            latest.etag
          )
          setTitle(overwritten.note.title)
          setContentBlocks(overwritten.note.contentBlocks)
          autoSave.resetWithServerVersion(overwritten.note, overwritten.etag)
          setIsConflictDialogOpen(false)
        } catch {
          await noteQuery.refetch()
          setIsConflictDialogOpen(true)
        }
      }}
    />
    </>
  )
}

function isNetworkError(error: unknown): boolean {
  if (!error) return false
  if (error instanceof TypeError) return true
  if (error instanceof Error) {
    const message = error.message.toLowerCase()
    return message.includes('network') || message.includes('fetch')
  }
  return false
}

