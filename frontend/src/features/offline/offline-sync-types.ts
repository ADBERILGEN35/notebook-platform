import type { NoteBlock } from '../../shared/types/api'

/** Persisted snapshot (title + blocks) for offline draft baseline or local edits. */
export type OfflineDraftSnapshot = {
  title: string
  contentBlocks: NoteBlock[]
}

export type OfflineDraftStatus =
  | 'DRAFT'
  | 'QUEUED'
  | 'SYNCING'
  | 'SYNCED'
  | 'CONFLICT'
  | 'FAILED'

/**
 * One row per noteId (single active draft). Stored in IndexedDB `offline_note_drafts`.
 */
export type OfflineNoteDraftRecord = {
  draftId: string
  noteId: string
  workspaceId: string
  notebookId: string
  baseEtag: string | null
  baseUpdatedAt: string
  baseSnapshot: OfflineDraftSnapshot
  localSnapshot: OfflineDraftSnapshot
  status: OfflineDraftStatus
  lastEditedAt: string
  queuedAt: string | null
  syncedAt: string | null
  conflictReason: string | null
  attemptCount: number
  lastError: string | null
}

/** UX-oriented sync states (documentation + future UI). */
export type OfflineSyncUxState =
  | 'online-clean'
  | 'online-dirty'
  | 'offline-readonly'
  | 'offline-editing-draft'
  | 'queued-for-sync'
  | 'syncing'
  | 'synced'
  | 'sync-conflict'
  | 'sync-failed'
