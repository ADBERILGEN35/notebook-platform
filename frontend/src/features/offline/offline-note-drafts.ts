import {
  isOfflineEditEnabled,
  offlineEditMaxDraftAgeDays,
  offlineEditMaxDrafts,
} from '../../shared/config/offline-feature-flags'
import { isOfflineNotesEnabled } from '../../shared/config/offline-feature-flags'
import { OFFLINE_DRAFTS_STORE, openOfflineDb } from './offline-db'
import type { OfflineDraftSnapshot, OfflineDraftStatus, OfflineNoteDraftRecord } from './offline-sync-types'

function canUseDraftStore(): boolean {
  return isOfflineNotesEnabled() && isOfflineEditEnabled()
}

export async function saveOfflineDraft(payload: {
  noteId: string
  workspaceId: string
  notebookId: string
  baseEtag: string | null
  baseUpdatedAt: string
  baseSnapshot: OfflineDraftSnapshot
  localSnapshot: OfflineDraftSnapshot
}): Promise<OfflineNoteDraftRecord | null> {
  if (!canUseDraftStore()) return null
  const db = await openOfflineDb()
  const now = new Date().toISOString()
  const existing = (await db.get(OFFLINE_DRAFTS_STORE, payload.noteId)) as OfflineNoteDraftRecord | undefined
  const record: OfflineNoteDraftRecord = {
    draftId: existing?.draftId ?? crypto.randomUUID(),
    noteId: payload.noteId,
    workspaceId: payload.workspaceId,
    notebookId: payload.notebookId,
    baseEtag: payload.baseEtag,
    baseUpdatedAt: payload.baseUpdatedAt,
    baseSnapshot: payload.baseSnapshot,
    localSnapshot: payload.localSnapshot,
    status: 'DRAFT',
    lastEditedAt: now,
    queuedAt: null,
    syncedAt: existing?.syncedAt ?? null,
    conflictReason: null,
    attemptCount: existing?.attemptCount ?? 0,
    lastError: null,
  }
  await db.put(OFFLINE_DRAFTS_STORE, record)
  await pruneOfflineDrafts(offlineEditMaxDrafts(), offlineEditMaxDraftAgeDays())
  return record
}

export async function getOfflineDraft(noteId: string): Promise<OfflineNoteDraftRecord | null> {
  if (!isOfflineNotesEnabled() || !isOfflineEditEnabled()) return null
  const db = await openOfflineDb()
  return ((await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineNoteDraftRecord | undefined) ?? null
}

export async function listOfflineDrafts(): Promise<OfflineNoteDraftRecord[]> {
  if (!isOfflineNotesEnabled() || !isOfflineEditEnabled()) return []
  const db = await openOfflineDb()
  return (await db.getAll(OFFLINE_DRAFTS_STORE)) as OfflineNoteDraftRecord[]
}

export async function listPendingDrafts(): Promise<OfflineNoteDraftRecord[]> {
  const all = await listOfflineDrafts()
  return all.filter((d) => d.status !== 'SYNCED')
}

export async function markDraftQueued(noteId: string): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineNoteDraftRecord | undefined
  if (!row) return
  row.status = 'QUEUED'
  row.queuedAt = new Date().toISOString()
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function markDraftSyncing(noteId: string): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineNoteDraftRecord | undefined
  if (!row) return
  row.status = 'SYNCING'
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function markDraftSynced(noteId: string): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineNoteDraftRecord | undefined
  if (!row) return
  row.status = 'SYNCED'
  row.syncedAt = new Date().toISOString()
  row.conflictReason = null
  row.lastError = null
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function markDraftConflict(noteId: string, reason: string): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineNoteDraftRecord | undefined
  if (!row) return
  row.status = 'CONFLICT'
  row.conflictReason = reason
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function markDraftFailed(noteId: string, message: string, requeue: boolean): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineNoteDraftRecord | undefined
  if (!row) return
  row.status = requeue ? 'QUEUED' : 'FAILED'
  row.lastError = message
  row.attemptCount = (row.attemptCount ?? 0) + 1
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function deleteOfflineDraft(noteId: string): Promise<void> {
  if (!isOfflineNotesEnabled()) return
  const db = await openOfflineDb()
  await db.delete(OFFLINE_DRAFTS_STORE, noteId)
}

export async function clearOfflineDrafts(): Promise<void> {
  try {
    const db = await openOfflineDb()
    const keys = await db.getAllKeys(OFFLINE_DRAFTS_STORE)
    await Promise.all(keys.map((k) => db.delete(OFFLINE_DRAFTS_STORE, k)))
  } catch {
    // DB may be missing or blocked
  }
}

export async function pruneOfflineDrafts(maxCount: number, maxAgeDays: number): Promise<void> {
  if (!isOfflineNotesEnabled()) return
  const db = await openOfflineDb()
  const all = (await db.getAll(OFFLINE_DRAFTS_STORE)) as OfflineNoteDraftRecord[]
  const cutoff = Date.now() - maxAgeDays * 24 * 60 * 60 * 1000
  const stale = all.filter((d) => Date.parse(d.lastEditedAt) < cutoff)
  for (const d of stale) {
    await db.delete(OFFLINE_DRAFTS_STORE, d.noteId)
  }
  const remaining = (await db.getAll(OFFLINE_DRAFTS_STORE)) as OfflineNoteDraftRecord[]
  if (remaining.length <= maxCount) return
  const sorted = remaining.sort((a, b) => Date.parse(b.lastEditedAt) - Date.parse(a.lastEditedAt))
  const drop = sorted.slice(maxCount)
  for (const d of drop) {
    await db.delete(OFFLINE_DRAFTS_STORE, d.noteId)
  }
}

/** Bump attempt without changing status (optional helper for policy layer). */
export async function bumpDraftAttempt(noteId: string): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineNoteDraftRecord | undefined
  if (!row) return
  row.attemptCount = (row.attemptCount ?? 0) + 1
  await db.put(OFFLINE_DRAFTS_STORE, row)
}
