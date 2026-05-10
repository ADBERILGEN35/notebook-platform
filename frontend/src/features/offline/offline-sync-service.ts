import { analyzeMerge, applyMerge, createNote, getNote, updateNote } from '../notes/note-api'
import { analyzeNoteConflict } from '../notes/utils/blocknote-merge'
import { createNoteSaveSnapshot } from '../notes/utils/note-save-snapshot'
import {
  isBackendMergeAnalysisEnabled,
  isBackendMergeApplyEnabled,
  offlineSyncMaxAttempts,
  offlineSyncRolloutMode,
} from '../../shared/config/offline-feature-flags'
import { saveOfflineNote } from './offline-note-cache'
import {
  deleteOfflineDraft,
  getOfflineDraft,
  getRawDraftRow,
  listOfflineDrafts,
  listPendingDrafts,
  markDraftConflict,
  markDraftFailed,
  markDraftSyncing,
  recoverStaleSyncingDrafts,
} from './offline-note-drafts'
import { mapHttpErrorToSyncPolicy } from './offline-sync-policy'
import { markSyncAttempt, markSyncSuccess, updateDraftCounters } from './offline-sync-diagnostics'

export type OfflineSyncResult =
  | { status: 'synced'; noteId: string }
  | { status: 'conflict'; noteId: string; reason: string }
  | { status: 'queued'; noteId: string; reason: string }
  | { status: 'failed'; noteId: string; reason: string }
  | { status: 'not_found'; noteId: string; reason: string }
  | { status: 'locked'; noteId: string; reason: string }

type DraftConflictData = {
  noteId: string
  reason: string
  localSnapshot: { title: string; contentBlocks: import('../../shared/types/api').NoteBlock[] }
  remote: Awaited<ReturnType<typeof getNote>>
  analysis: ReturnType<typeof analyzeNoteConflict>
}

async function resolveDraftById(id: string) {
  const direct = await getOfflineDraft(id)
  if (direct) return direct
  const rows = await listPendingDrafts()
  return rows.find((r) => r.draftId === id) ?? null
}

export async function syncOfflineDraft(draftIdOrNoteId: string): Promise<OfflineSyncResult> {
  if (offlineSyncRolloutMode() === 'disabled') {
    return { status: 'failed', noteId: draftIdOrNoteId, reason: 'SYNC_DISABLED_BY_ROLLOUT_MODE' }
  }
  markSyncAttempt()
  await recoverStaleSyncingDrafts()
  const draft = await resolveDraftById(draftIdOrNoteId)
  if (!draft) {
    const raw = await getRawDraftRow(draftIdOrNoteId)
    if (raw.exists && raw.locked) {
      return { status: 'locked', noteId: draftIdOrNoteId, reason: 'ENCRYPTED_KEY_UNAVAILABLE' }
    }
    return { status: 'failed', noteId: draftIdOrNoteId, reason: 'DRAFT_NOT_FOUND' }
  }
  if (!draft.baseEtag) {
    await markDraftFailed(draft.noteId, 'MISSING_BASE_ETAG', false)
    return { status: 'failed', noteId: draft.noteId, reason: 'MISSING_BASE_ETAG' }
  }
  if (draft.status === 'CONFLICT') {
    return { status: 'conflict', noteId: draft.noteId, reason: 'CONFLICT_REQUIRES_RESOLUTION' }
  }
  if (draft.attemptCount >= offlineSyncMaxAttempts()) {
    await markDraftFailed(draft.noteId, 'MAX_SYNC_ATTEMPTS_EXCEEDED', false)
    return { status: 'failed', noteId: draft.noteId, reason: 'MAX_SYNC_ATTEMPTS_EXCEEDED' }
  }
  await markDraftSyncing(draft.noteId)
  try {
    const saved = await updateNote(
      draft.noteId,
      {
        title: draft.localSnapshot.title,
        contentBlocks: draft.localSnapshot.contentBlocks,
      },
      draft.baseEtag,
    )
    await saveOfflineNote(saved.note, saved.etag)
    await deleteOfflineDraft(draft.noteId)
    markSyncSuccess()
    return { status: 'synced', noteId: draft.noteId }
  } catch (err) {
    const policy = mapHttpErrorToSyncPolicy(err)
    if (policy.outcome === 'conflict') {
      await markDraftConflict(draft.noteId, policy.reason)
      return { status: 'conflict', noteId: draft.noteId, reason: policy.reason }
    }
    if (policy.outcome === 'not_found') {
      await markDraftFailed(draft.noteId, policy.reason, false)
      return { status: 'not_found', noteId: draft.noteId, reason: policy.reason }
    }
    if (policy.outcome === 'failed') {
      await markDraftFailed(draft.noteId, policy.reason, policy.requeue)
      return policy.requeue
        ? { status: 'queued', noteId: draft.noteId, reason: policy.reason }
        : { status: 'failed', noteId: draft.noteId, reason: policy.reason }
    }
    return { status: 'failed', noteId: draft.noteId, reason: 'SYNC_POLICY_UNEXPECTED' }
  }
}

export async function getOfflineDraftConflictData(noteId: string): Promise<DraftConflictData | null> {
  const draft = await getOfflineDraft(noteId)
  if (!draft || draft.status !== 'CONFLICT') return null
  const remote = await getNote(noteId)
  const analysis = analyzeNoteConflict(
    createNoteSaveSnapshot(draft.baseSnapshot.title, draft.baseSnapshot.contentBlocks),
    createNoteSaveSnapshot(draft.localSnapshot.title, draft.localSnapshot.contentBlocks),
    createNoteSaveSnapshot(remote.note.title, remote.note.contentBlocks),
  )
  return {
    noteId,
    reason: draft.conflictReason || 'NOTE_CONFLICT',
    localSnapshot: draft.localSnapshot,
    remote,
    analysis,
  }
}

export async function syncPendingDrafts(): Promise<OfflineSyncResult[]> {
  if (offlineSyncRolloutMode() === 'disabled') return []
  await recoverStaleSyncingDrafts()
  const rows = await listPendingDrafts()
  const out: OfflineSyncResult[] = []
  for (const row of rows) {
    if (row.status === 'SYNCED' || row.status === 'SYNCING' || row.status === 'CONFLICT') continue
    out.push(await syncOfflineDraft(row.noteId))
  }
  return out
}

export async function resolveDraftConflictSuggestedMerge(noteId: string): Promise<OfflineSyncResult> {
  const draft = await getOfflineDraft(noteId)
  if (!draft) return { status: 'failed', noteId, reason: 'DRAFT_NOT_FOUND' }
  const remote = await getNote(noteId)
  if (isBackendMergeAnalysisEnabled() && isBackendMergeApplyEnabled()) {
    try {
      const analyzed = await analyzeMerge(noteId, {
        base: {
          ...createNoteSaveSnapshot(draft.baseSnapshot.title, draft.baseSnapshot.contentBlocks),
          etag: draft.baseEtag,
        },
        local: draft.localSnapshot,
        clientMergeVersion: 1,
      })
      if (!analyzed.suggested) {
        await markDraftConflict(noteId, 'NOTE_MERGE_CONFLICTS')
        return { status: 'conflict', noteId, reason: 'NOTE_MERGE_CONFLICTS' }
      }
      const applied = await applyMerge(noteId, {
        base: {
          ...createNoteSaveSnapshot(draft.baseSnapshot.title, draft.baseSnapshot.contentBlocks),
          etag: draft.baseEtag,
        },
        local: draft.localSnapshot,
        expectedRemoteEtag: analyzed.remoteEtag,
        mergeVersion: analyzed.mergeVersion,
        idempotencyKey: window.crypto?.randomUUID?.(),
      })
      await saveOfflineNote(
        {
          ...remote.note,
          title: applied.title,
          contentBlocks: applied.contentBlocks,
        },
        applied.etag,
      )
      await deleteOfflineDraft(noteId)
      return { status: 'synced', noteId }
    } catch (err) {
      const policy = mapHttpErrorToSyncPolicy(err)
      if (policy.outcome === 'conflict') {
        await markDraftConflict(noteId, policy.reason)
        return { status: 'conflict', noteId, reason: policy.reason }
      }
    }
  }
  const analysis = analyzeNoteConflict(
    createNoteSaveSnapshot(draft.baseSnapshot.title, draft.baseSnapshot.contentBlocks),
    createNoteSaveSnapshot(draft.localSnapshot.title, draft.localSnapshot.contentBlocks),
    createNoteSaveSnapshot(remote.note.title, remote.note.contentBlocks),
  )
  if (!analysis.suggestion) {
    return { status: 'failed', noteId, reason: 'NO_SAFE_MERGE' }
  }
  try {
    const saved = await updateNote(
      noteId,
      {
        title: analysis.suggestion.title,
        contentBlocks: analysis.suggestion.contentBlocks,
      },
      remote.etag,
    )
    await saveOfflineNote(saved.note, saved.etag)
    await deleteOfflineDraft(noteId)
    return { status: 'synced', noteId }
  } catch (err) {
    const policy = mapHttpErrorToSyncPolicy(err)
    if (policy.outcome === 'conflict') {
      await markDraftConflict(noteId, policy.reason)
      return { status: 'conflict', noteId, reason: policy.reason }
    }
    if (policy.outcome === 'failed') {
      await markDraftFailed(noteId, policy.reason, policy.requeue)
      return policy.requeue
        ? { status: 'queued', noteId, reason: policy.reason }
        : { status: 'failed', noteId, reason: policy.reason }
    }
    if (policy.outcome === 'not_found') {
      await markDraftFailed(noteId, policy.reason, false)
      return { status: 'not_found', noteId, reason: policy.reason }
    }
    return { status: 'failed', noteId, reason: 'SYNC_POLICY_UNEXPECTED' }
  }
}

export async function resolveDraftConflictSaveAsCopy(noteId: string): Promise<{ noteId: string } | null> {
  const draft = await getOfflineDraft(noteId)
  if (!draft) return null
  const created = await createNote(draft.notebookId, {
    title: `${draft.localSnapshot.title || 'Offline draft'} (offline copy)`,
    contentBlocks: draft.localSnapshot.contentBlocks,
  })
  await deleteOfflineDraft(noteId)
  return { noteId: created.id }
}

export async function resolveDraftConflictOverwrite(noteId: string): Promise<OfflineSyncResult> {
  const draft = await getOfflineDraft(noteId)
  if (!draft) return { status: 'failed', noteId, reason: 'DRAFT_NOT_FOUND' }
  const latest = await getNote(noteId)
  try {
    const saved = await updateNote(
      noteId,
      {
        title: draft.localSnapshot.title,
        contentBlocks: draft.localSnapshot.contentBlocks,
      },
      latest.etag,
    )
    await saveOfflineNote(saved.note, saved.etag)
    await deleteOfflineDraft(noteId)
    return { status: 'synced', noteId }
  } catch (err) {
    const policy = mapHttpErrorToSyncPolicy(err)
    if (policy.outcome === 'conflict') {
      await markDraftConflict(noteId, policy.reason)
      return { status: 'conflict', noteId, reason: policy.reason }
    }
    if (policy.outcome === 'failed') {
      await markDraftFailed(noteId, policy.reason, policy.requeue)
      return policy.requeue
        ? { status: 'queued', noteId, reason: policy.reason }
        : { status: 'failed', noteId, reason: policy.reason }
    }
    if (policy.outcome === 'not_found') {
      await markDraftFailed(noteId, policy.reason, false)
      return { status: 'not_found', noteId, reason: policy.reason }
    }
    return { status: 'failed', noteId, reason: 'SYNC_POLICY_UNEXPECTED' }
  }
}

export async function discardDraft(noteId: string): Promise<void> {
  await deleteOfflineDraft(noteId)
}

export async function refreshOfflineDraftDiagnostics() {
  const rows = await listOfflineDrafts()
  updateDraftCounters({
    pending: rows.filter((r) => r.status !== 'SYNCED').length,
    conflict: rows.filter((r) => r.status === 'CONFLICT').length,
    failed: rows.filter((r) => r.status === 'FAILED').length,
  })
}
