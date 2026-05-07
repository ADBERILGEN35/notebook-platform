import { useMutation, useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { listComments, createComment, resolveComment, reopenComment } from '../features/comments/comments-api'
import { useNoteAutoSave } from '../features/notes/hooks/useNoteAutoSave'
import { BlockNoteEditor } from '../features/notes/components/BlockNoteEditor'
import { getNote, type NoteWithEtag, updateNote } from '../features/notes/note-api'
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

type RightTab = 'comments' | 'versions' | 'info'
const AUTO_SAVE_ENABLED = true
const AUTO_SAVE_DEBOUNCE_MS = 1500
const AUTO_SAVE_MIN_CHANGE_INTERVAL_MS = 1000

export function NotePage() {
  const { noteId } = useParams()
  const [tab, setTab] = useState<RightTab>('comments')
  const [title, setTitle] = useState('')
  const [contentBlocks, setContentBlocks] = useState<NoteBlock[]>(createEmptyDocument())
  const [commentInput, setCommentInput] = useState('')
  const [contentParseError, setContentParseError] = useState<string | null>(null)
  const [hydratedNoteSignature, setHydratedNoteSignature] = useState<string | null>(null)

  const noteQuery = useQuery({
    queryKey: ['note', noteId],
    queryFn: () => getNote(noteId!),
    enabled: Boolean(noteId),
  })
  const commentsQuery = useQuery({
    queryKey: ['comments', noteId],
    queryFn: () => listComments(noteId!, 0, 20),
    enabled: Boolean(noteId),
  })
  const versionsQuery = useQuery({
    queryKey: ['versions', noteId],
    queryFn: () => listVersions(noteId!, 0, 20),
    enabled: Boolean(noteId),
  })

  const saveMutation = useMutation({
    mutationFn: (payload: { title: string; contentBlocks: NoteBlock[]; ifMatch: string | null }) =>
      updateNote(noteId!, payload, payload.ifMatch),
    onSuccess: () => {
      noteQuery.refetch()
      versionsQuery.refetch()
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
    },
  })

  const autoSave = useNoteAutoSave({
    noteId: noteId ?? '',
    title,
    contentBlocks,
    enabled: AUTO_SAVE_ENABLED,
    debounceMs: AUTO_SAVE_DEBOUNCE_MS,
    minChangeIntervalMs: AUTO_SAVE_MIN_CHANGE_INTERVAL_MS,
    baseUpdatedAt: noteQuery.data?.note.updatedAt ?? null,
    initialEtag: noteQuery.data?.etag ?? null,
    saveNote: async (payload) => saveMutation.mutateAsync(payload),
  })

  const note = noteQuery.data?.note
  const noteEtag = noteQuery.data?.etag ?? null
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
  if (noteQuery.isError) return <ErrorAlert error={noteQuery.error} />

  return (
    <div className="flex h-full gap-3">
      <section className="flex-1 rounded-lg border border-slate-200 bg-white p-4">
        {contentParseError ? <ErrorAlert error={new Error(contentParseError)} /> : null}
        <Input
          data-testid="note-title-input"
          value={title}
          onChange={(event) => {
            setTitle(event.target.value)
            autoSave.scheduleSave()
          }}
          className="mb-2 text-xl"
        />
        <BlockNoteEditor
          initialContentBlocks={contentBlocks}
          isSaving={autoSave.saveState === 'saving'}
          onChange={(blocks) => {
            setContentBlocks(blocks)
            autoSave.scheduleSave()
          }}
        />
        {autoSave.saveState === 'conflict' ? (
          <div
            data-testid="conflict-banner"
            className="mt-2 rounded border border-amber-300 bg-amber-50 p-2 text-sm text-amber-800"
          >
            This note was changed elsewhere. Reload the latest version before continuing.
            <button className="ml-2 underline" onClick={() => noteQuery.refetch()}>
              Reload latest
            </button>
          </div>
        ) : null}
        <div className="mt-3 flex items-center justify-between">
          <p className="text-xs text-slate-500">
            Updated: {note?.updatedAt ? new Date(note.updatedAt).toLocaleString() : '-'}
          </p>
          <div className="flex items-center gap-3">
            <span data-testid="save-status" className="text-xs text-slate-500">
              {saveStatusLabel}
            </span>
            <span data-testid="autosave-indicator" className="text-xs text-slate-400">
              Auto-save {AUTO_SAVE_ENABLED ? 'on' : 'off'} ({AUTO_SAVE_DEBOUNCE_MS}ms)
            </span>
            <Button
              data-testid="note-save-button"
              className="bg-primary-600 text-white hover:bg-primary-700"
              onClick={() => autoSave.saveNow()}
              disabled={autoSave.saveState === 'saving'}
            >
              Save now
            </Button>
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
      </section>
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
      />
    </div>
  )
}

