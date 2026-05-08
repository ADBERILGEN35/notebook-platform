import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { listComments, createComment, resolveComment, reopenComment } from '../features/comments/comments-api'
import { useNoteAutoSave } from '../features/notes/hooks/useNoteAutoSave'
import { BlockNoteEditor } from '../features/notes/components/BlockNoteEditor'
import { createNote, getNote, type NoteWithEtag, updateNote } from '../features/notes/note-api'
import {
  createEmptyDocument,
  toBlockNoteDocument,
} from '../features/notes/utils/blocknote-serialization'
import { listVersions, restoreVersion } from '../features/versions/versions-api'
import { Button } from '../shared/components/Button'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { Input } from '../shared/components/Input'
import { LoadingState } from '../shared/components/LoadingState'
import { RightPanel } from '../shared/layout/RightPanel'
import type { NoteBlock } from '../shared/types/api'
import { useEffect, useMemo, useState } from 'react'
import { useMediaQuery } from '../shared/hooks/useMediaQuery'
import { RightPanelDrawer } from '../shared/layout/RightPanelDrawer'
import { extractPlainTextFromBlocks } from '../features/notes/utils/blocknote-serialization'
import { createNoteSaveSnapshot } from '../features/notes/utils/note-save-snapshot'
import { analyzeNoteConflict } from '../features/notes/utils/blocknote-merge'
import { NoteConflictResolutionDialog } from '../features/notes/components/NoteConflictResolutionDialog'
import { useOnlineStatus } from '../shared/hooks/useOnlineStatus'
import { getOfflineNote, saveOfflineNote } from '../features/offline/offline-note-cache'
import { isOfflineNotesEnabled } from '../shared/config/offline-feature-flags'

type RightTab = 'comments' | 'versions' | 'info'
const AUTO_SAVE_ENABLED = true
const AUTO_SAVE_DEBOUNCE_MS = 1500
const AUTO_SAVE_MIN_CHANGE_INTERVAL_MS = 1000

export function NotePage() {
  const { noteId } = useParams()
  const navigate = useNavigate()
  const [tab, setTab] = useState<RightTab>('comments')
  const [title, setTitle] = useState('')
  const [contentBlocks, setContentBlocks] = useState<NoteBlock[]>(createEmptyDocument())
  const [commentInput, setCommentInput] = useState('')
  const [contentParseError, setContentParseError] = useState<string | null>(null)
  const [hydratedNoteSignature, setHydratedNoteSignature] = useState<string | null>(null)
  const [isRightPanelOpen, setIsRightPanelOpen] = useState(false)
  const [isConflictDialogOpen, setIsConflictDialogOpen] = useState(false)
  const isDesktop = useMediaQuery('(min-width: 1024px)')
  const { isOnline } = useOnlineStatus()
  const offlineEnabled = isOfflineNotesEnabled()

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
  const isOfflineReadOnly = noteQuery.data?.source === 'offline'
  const commentsQuery = useQuery({
    queryKey: ['comments', noteId],
    queryFn: () => listComments(noteId!, 0, 20),
    enabled: Boolean(noteId) && isOnline && !isOfflineReadOnly,
  })
  const versionsQuery = useQuery({
    queryKey: ['versions', noteId],
    queryFn: () => listVersions(noteId!, 0, 20),
    enabled: Boolean(noteId) && isOnline && !isOfflineReadOnly,
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
    enabled: AUTO_SAVE_ENABLED && !isOfflineReadOnly,
    debounceMs: AUTO_SAVE_DEBOUNCE_MS,
    minChangeIntervalMs: AUTO_SAVE_MIN_CHANGE_INTERVAL_MS,
    baseUpdatedAt: noteQuery.data?.note.updatedAt ?? null,
    initialEtag: noteQuery.data?.etag ?? null,
    saveNote: async (payload) => saveMutation.mutateAsync(payload),
  })

  const note = noteQuery.data?.note
  const noteEtag = noteQuery.data?.etag ?? null
  const conflictInfo = autoSave.conflictInfo
  const conflictRemoteQuery = useQuery({
    queryKey: ['note', noteId, 'conflict-remote'],
    queryFn: () => getNote(noteId!),
    enabled: Boolean(noteId && isConflictDialogOpen && conflictInfo && !isOfflineReadOnly),
  })
  const mergeAnalysis = useMemo(() => {
    if (!conflictInfo || !conflictRemoteQuery.data) return null
    return analyzeNoteConflict(
      conflictInfo.baseSnapshot,
      conflictInfo.localSnapshot,
      createNoteSaveSnapshot(
        conflictRemoteQuery.data.note.title,
        conflictRemoteQuery.data.note.contentBlocks,
      ),
    )
  }, [conflictInfo, conflictRemoteQuery.data])
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

  const saveStatusLabel = useMemo(() => {
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
  }, [autoSave.saveState])

  if (noteQuery.isLoading) return <LoadingState />
  if (noteQuery.isError && (noteQuery.error as Error).message === 'OFFLINE_NOTE_UNAVAILABLE') {
    return (
      <div className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800">
        This note is not available offline.
      </div>
    )
  }
  if (noteQuery.isError) return <ErrorAlert error={noteQuery.error} />

  return (
    <>
    <div className="flex h-full flex-col gap-3 lg:flex-row">
      <section className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white p-3 sm:p-4">
        {isOfflineReadOnly ? (
          <div className="mb-2 rounded border border-amber-300 bg-amber-50 p-2 text-sm text-amber-800">
            Offline copy loaded. Editing is disabled while offline.
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
          disabled={isOfflineReadOnly}
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
              Auto-save {AUTO_SAVE_ENABLED && !isOfflineReadOnly ? 'on' : 'off'} ({AUTO_SAVE_DEBOUNCE_MS}ms)
            </span>
            <Button
              data-testid="note-save-button"
              className="bg-primary-600 text-white hover:bg-primary-700"
              onClick={() => autoSave.saveNow()}
              disabled={autoSave.saveState === 'saving' || isOfflineReadOnly}
            >
              Save now
            </Button>
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
            disabled={isOfflineReadOnly}
          />
        </RightPanelDrawer>
      )}
    </div>
    <NoteConflictResolutionDialog
      open={Boolean(conflictInfo) && isConflictDialogOpen}
      onClose={() => setIsConflictDialogOpen(false)}
      localSnapshot={conflictInfo?.localSnapshot ?? { title: '', contentBlocks: createEmptyDocument(), serialized: '' }}
      serverTitle={serverTitleForDialog}
      serverPreview={serverPreview}
      localPreview={localConflictPreview}
      canSaveCopy={Boolean(note?.notebookId)}
      mergeAnalysis={mergeAnalysis}
      remoteLoading={conflictRemoteQuery.isFetching}
      onApplySuggestedMerge={async () => {
        if (!mergeAnalysis?.suggestion || !conflictRemoteQuery.data || !noteId) return
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
        const confirmed = window.confirm('Reload latest and discard local unsaved changes?')
        if (!confirmed) return
        await noteQuery.refetch()
        autoSave.clearConflict()
        setIsConflictDialogOpen(false)
      }}
      onSaveCopy={async () => {
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

