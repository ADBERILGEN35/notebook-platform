import {
  offlineSyncMaxAttempts,
  offlineSyncStaleMinutes,
  isOfflineEditEnabled,
  isOfflineDraftEncryptionRequired,
  isOfflineEncryptionEnabled,
  offlineEditMaxDraftAgeDays,
  offlineEditMaxDrafts,
} from '../../shared/config/offline-feature-flags'
import { isOfflineNotesEnabled } from '../../shared/config/offline-feature-flags'
import { OFFLINE_DRAFTS_STORE, openOfflineDb } from './offline-db'
import {
  decryptJson,
  encryptJson,
  ensureOfflineEncryptionKey,
  hasOfflineEncryptionKey,
  isOfflineCryptoSupported,
  type EncryptedJsonPayload,
} from './offline-crypto'
import type { OfflineDraftSnapshot, OfflineNoteDraftRecord } from './offline-sync-types'

function canUseDraftStore(): boolean {
  return isOfflineNotesEnabled() && isOfflineEditEnabled()
}

type OfflineDraftDbRow = Omit<
  OfflineNoteDraftRecord,
  'baseEtag' | 'baseUpdatedAt' | 'baseSnapshot' | 'localSnapshot' | 'lastError'
> & {
  baseEtag?: string | null
  baseUpdatedAt?: string | null
  baseSnapshot?: OfflineDraftSnapshot
  localSnapshot?: OfflineDraftSnapshot
  lastError?: string | null
  encryptedPayload?: EncryptedJsonPayload
}

type OfflineDraftSensitivePayload = {
  baseEtag: string | null
  baseUpdatedAt: string
  baseSnapshot: OfflineDraftSnapshot
  localSnapshot: OfflineDraftSnapshot
  lastError: string | null
}

export type OfflineDraftOverview = {
  noteId: string
  draftId: string
  workspaceId: string
  notebookId: string
  status: OfflineNoteDraftRecord['status']
  lastEditedAt: string
  queuedAt: string | null
  syncedAt: string | null
  attemptCount: number
  baseEtag: string | null
  lastError: string | null
  conflictReason: string | null
  title: string
  locked: boolean
}

function shouldUseDraftEncryption(): boolean {
  return isOfflineEncryptionEnabled()
}

async function ensureDraftCryptoReady(): Promise<boolean> {
  if (!shouldUseDraftEncryption()) return true
  if (!isOfflineCryptoSupported()) return false
  if (hasOfflineEncryptionKey()) return true
  return ensureOfflineEncryptionKey()
}

function hasPlaintextSensitiveFields(row: OfflineDraftDbRow): boolean {
  return Boolean(row.baseSnapshot || row.localSnapshot || row.baseUpdatedAt)
}

async function toPublicDraft(row: OfflineDraftDbRow, db: Awaited<ReturnType<typeof openOfflineDb>>): Promise<OfflineNoteDraftRecord | null> {
  if (row.encryptedPayload) {
    try {
      if (!(await ensureDraftCryptoReady())) return null
      const sensitive = await decryptJson<OfflineDraftSensitivePayload>(row.encryptedPayload)
      return {
        draftId: row.draftId,
        noteId: row.noteId,
        workspaceId: row.workspaceId,
        notebookId: row.notebookId,
        baseEtag: sensitive.baseEtag,
        baseUpdatedAt: sensitive.baseUpdatedAt,
        baseSnapshot: sensitive.baseSnapshot,
        localSnapshot: sensitive.localSnapshot,
        status: row.status,
        lastEditedAt: row.lastEditedAt,
        queuedAt: row.queuedAt,
        syncedAt: row.syncedAt,
        conflictReason: row.conflictReason,
        attemptCount: row.attemptCount,
        lastError: sensitive.lastError,
      }
    } catch {
      return null
    }
  }
  if (isOfflineDraftEncryptionRequired()) {
    if (hasPlaintextSensitiveFields(row)) {
      await db.delete(OFFLINE_DRAFTS_STORE, row.noteId)
    }
    return null
  }
  if (!row.baseSnapshot || !row.localSnapshot || !row.baseUpdatedAt) return null
  return {
    draftId: row.draftId,
    noteId: row.noteId,
    workspaceId: row.workspaceId,
    notebookId: row.notebookId,
    baseEtag: row.baseEtag ?? null,
    baseUpdatedAt: row.baseUpdatedAt,
    baseSnapshot: row.baseSnapshot,
    localSnapshot: row.localSnapshot,
    status: row.status,
    lastEditedAt: row.lastEditedAt,
    queuedAt: row.queuedAt,
    syncedAt: row.syncedAt,
    conflictReason: row.conflictReason,
    attemptCount: row.attemptCount,
    lastError: row.lastError ?? null,
  }
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
  const encryptionReady = await ensureDraftCryptoReady()
  if (isOfflineDraftEncryptionRequired() && !encryptionReady) return null
  const useEncryption = shouldUseDraftEncryption() && encryptionReady
  const db = await openOfflineDb()
  const now = new Date().toISOString()
  const existing = (await db.get(OFFLINE_DRAFTS_STORE, payload.noteId)) as OfflineDraftDbRow | undefined
  const sensitive: OfflineDraftSensitivePayload = {
    baseEtag: payload.baseEtag,
    baseUpdatedAt: payload.baseUpdatedAt,
    baseSnapshot: payload.baseSnapshot,
    localSnapshot: payload.localSnapshot,
    lastError: null,
  }
  const encryptedPayload = useEncryption ? await encryptJson(sensitive) : undefined
  const record: OfflineDraftDbRow = {
    draftId: existing?.draftId ?? crypto.randomUUID(),
    noteId: payload.noteId,
    workspaceId: payload.workspaceId,
    notebookId: payload.notebookId,
    status: 'DRAFT',
    lastEditedAt: now,
    queuedAt: null,
    syncedAt: existing?.syncedAt ?? null,
    conflictReason: null,
    attemptCount: existing?.attemptCount ?? 0,
    encryptedPayload,
    baseEtag: useEncryption ? undefined : payload.baseEtag,
    baseUpdatedAt: useEncryption ? undefined : payload.baseUpdatedAt,
    baseSnapshot: useEncryption ? undefined : payload.baseSnapshot,
    localSnapshot: useEncryption ? undefined : payload.localSnapshot,
    lastError: useEncryption ? undefined : null,
  }
  await db.put(OFFLINE_DRAFTS_STORE, record)
  await pruneOfflineDrafts(offlineEditMaxDrafts(), offlineEditMaxDraftAgeDays())
  return toPublicDraft(record, db)
}

export async function getOfflineDraft(noteId: string): Promise<OfflineNoteDraftRecord | null> {
  if (!isOfflineNotesEnabled() || !isOfflineEditEnabled()) return null
  if (isOfflineDraftEncryptionRequired() && !(await ensureDraftCryptoReady())) return null
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineDraftDbRow | undefined
  if (!row) return null
  return toPublicDraft(row, db)
}

export async function listOfflineDrafts(): Promise<OfflineNoteDraftRecord[]> {
  if (!isOfflineNotesEnabled() || !isOfflineEditEnabled()) return []
  if (isOfflineDraftEncryptionRequired() && !(await ensureDraftCryptoReady())) return []
  const db = await openOfflineDb()
  const all = (await db.getAll(OFFLINE_DRAFTS_STORE)) as OfflineDraftDbRow[]
  const out: OfflineNoteDraftRecord[] = []
  for (const row of all) {
    const decoded = await toPublicDraft(row, db)
    if (decoded) out.push(decoded)
  }
  return out
}

export async function listPendingDrafts(): Promise<OfflineNoteDraftRecord[]> {
  await recoverStaleSyncingDrafts()
  const all = await listOfflineDrafts()
  return all.filter((d) => d.status !== 'SYNCED' && d.status !== 'CONFLICT')
}

export async function markDraftQueued(noteId: string): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineDraftDbRow | undefined
  if (!row) return
  row.status = 'QUEUED'
  row.queuedAt = new Date().toISOString()
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function markDraftSyncing(noteId: string): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineDraftDbRow | undefined
  if (!row) return
  row.attemptCount = (row.attemptCount ?? 0) + 1
  row.status = 'SYNCING'
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function markDraftSynced(noteId: string): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineDraftDbRow | undefined
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
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineDraftDbRow | undefined
  if (!row) return
  row.status = 'CONFLICT'
  row.conflictReason = reason
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function markDraftFailed(noteId: string, message: string, requeue: boolean): Promise<void> {
  if (!canUseDraftStore()) return
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineDraftDbRow | undefined
  if (!row) return
  row.status = requeue ? 'QUEUED' : 'FAILED'
  if (row.encryptedPayload && (await ensureDraftCryptoReady())) {
    try {
      const sensitive = await decryptJson<OfflineDraftSensitivePayload>(row.encryptedPayload)
      row.encryptedPayload = await encryptJson({
        ...sensitive,
        lastError: message,
      })
    } catch {
      // keep status updates even if payload re-encryption fails
    }
  } else {
    row.lastError = message
  }
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
  const all = (await db.getAll(OFFLINE_DRAFTS_STORE)) as OfflineDraftDbRow[]
  const cutoff = Date.now() - maxAgeDays * 24 * 60 * 60 * 1000
  const stale = all.filter((d) => Date.parse(d.lastEditedAt) < cutoff)
  for (const d of stale) {
    await db.delete(OFFLINE_DRAFTS_STORE, d.noteId)
  }
  const remaining = (await db.getAll(OFFLINE_DRAFTS_STORE)) as OfflineDraftDbRow[]
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
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineDraftDbRow | undefined
  if (!row) return
  row.attemptCount = (row.attemptCount ?? 0) + 1
  await db.put(OFFLINE_DRAFTS_STORE, row)
}

export async function getRawDraftRow(noteId: string): Promise<{ exists: boolean; locked: boolean }> {
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_DRAFTS_STORE, noteId)) as OfflineDraftDbRow | undefined
  if (!row) return { exists: false, locked: false }
  if (!row.encryptedPayload) return { exists: true, locked: false }
  const ready = await ensureDraftCryptoReady()
  return { exists: true, locked: !ready }
}

export async function listOfflineDraftOverview(): Promise<OfflineDraftOverview[]> {
  if (!isOfflineNotesEnabled() || !isOfflineEditEnabled()) return []
  const db = await openOfflineDb()
  const rows = (await db.getAll(OFFLINE_DRAFTS_STORE)) as OfflineDraftDbRow[]
  const out: OfflineDraftOverview[] = []
  for (const row of rows) {
    const decoded = await toPublicDraft(row, db)
    if (decoded) {
      out.push({
        noteId: decoded.noteId,
        draftId: decoded.draftId,
        workspaceId: decoded.workspaceId,
        notebookId: decoded.notebookId,
        status: decoded.status,
        lastEditedAt: decoded.lastEditedAt,
        queuedAt: decoded.queuedAt,
        syncedAt: decoded.syncedAt,
        attemptCount: decoded.attemptCount,
        baseEtag: decoded.baseEtag,
        lastError: decoded.lastError,
        conflictReason: decoded.conflictReason,
        title: decoded.localSnapshot.title || '(untitled)',
        locked: false,
      })
      continue
    }
    if (row.encryptedPayload) {
      out.push({
        noteId: row.noteId,
        draftId: row.draftId,
        workspaceId: row.workspaceId,
        notebookId: row.notebookId,
        status: row.status,
        lastEditedAt: row.lastEditedAt,
        queuedAt: row.queuedAt,
        syncedAt: row.syncedAt,
        attemptCount: row.attemptCount,
        baseEtag: row.baseEtag ?? null,
        lastError: null,
        conflictReason: row.conflictReason,
        title: '(encrypted draft)',
        locked: true,
      })
    }
  }
  return out.sort((a, b) => Date.parse(b.lastEditedAt) - Date.parse(a.lastEditedAt))
}

export async function recoverStaleSyncingDrafts(): Promise<number> {
  if (!isOfflineNotesEnabled() || !isOfflineEditEnabled()) return 0
  const db = await openOfflineDb()
  const rows = (await db.getAll(OFFLINE_DRAFTS_STORE)) as OfflineDraftDbRow[]
  const thresholdMs = offlineSyncStaleMinutes() * 60 * 1000
  const now = Date.now()
  let changed = 0
  for (const row of rows) {
    if (row.status !== 'SYNCING') continue
    const age = now - Date.parse(row.lastEditedAt)
    if (Number.isFinite(age) && age >= thresholdMs) {
      row.status = 'QUEUED'
      row.lastError = row.lastError ?? 'SYNC_STALE_RECOVERED'
      await db.put(OFFLINE_DRAFTS_STORE, row)
      changed += 1
    }
  }
  return changed
}

export function isDraftLockedError(reason: string | null | undefined): boolean {
  return reason === 'ENCRYPTED_KEY_UNAVAILABLE' || reason === 'OFFLINE_ENCRYPTION_KEY_UNAVAILABLE'
}

export function isDraftRetryable(status: OfflineNoteDraftRecord['status'], attempts: number): boolean {
  return (status === 'DRAFT' || status === 'QUEUED' || status === 'FAILED') && attempts < offlineSyncMaxAttempts()
}
