import { deleteDB } from 'idb'
import type { Note } from '../../shared/types/api'
import {
  isOfflineCacheEncryptionEnabled,
  isOfflineEncryptionEnabled,
  isOfflineNotesEnabled,
  offlineNotesMaxItems,
} from '../../shared/config/offline-feature-flags'
import { OFFLINE_DB_NAME, OFFLINE_NOTES_STORE, openOfflineDb } from './offline-db'
import {
  decryptJson,
  encryptJson,
  ensureOfflineEncryptionKey,
  hasOfflineEncryptionKey,
  isOfflineCryptoSupported,
  type EncryptedJsonPayload,
} from './offline-crypto'

export type OfflineNoteEntry = {
  noteId: string
  note: Note
  etag: string | null
  cachedAt: string
}

type OfflineNoteSensitivePayload = {
  note: Note
  etag: string | null
}

type OfflineNoteDbRow = {
  noteId: string
  cachedAt: string
  note?: Note
  etag?: string | null
  encryptedPayload?: EncryptedJsonPayload
}

function shouldUseNoteEncryption(): boolean {
  return isOfflineEncryptionEnabled() && isOfflineCacheEncryptionEnabled()
}

async function cryptoReadyForCache(): Promise<boolean> {
  if (!shouldUseNoteEncryption()) return true
  if (!isOfflineCryptoSupported()) return false
  if (hasOfflineEncryptionKey()) return true
  return ensureOfflineEncryptionKey()
}

async function decodeRow(row: OfflineNoteDbRow): Promise<OfflineNoteEntry | null> {
  if (row.encryptedPayload) {
    if (!(await cryptoReadyForCache())) return null
    try {
      const sensitive = await decryptJson<OfflineNoteSensitivePayload>(row.encryptedPayload)
      return {
        noteId: row.noteId,
        note: sensitive.note,
        etag: sensitive.etag,
        cachedAt: row.cachedAt,
      }
    } catch {
      return null
    }
  }
  if (!row.note) return null
  return {
    noteId: row.noteId,
    note: row.note,
    etag: row.etag ?? null,
    cachedAt: row.cachedAt,
  }
}

export async function saveOfflineNote(note: Note, etag: string | null) {
  if (!isOfflineNotesEnabled()) return
  const ready = await cryptoReadyForCache()
  const db = await openOfflineDb()
  const useEncryption = shouldUseNoteEncryption() && ready
  const row: OfflineNoteDbRow = {
    noteId: note.id,
    cachedAt: new Date().toISOString(),
    encryptedPayload: useEncryption ? await encryptJson({ note, etag } satisfies OfflineNoteSensitivePayload) : undefined,
    note: useEncryption ? undefined : note,
    etag: useEncryption ? undefined : etag,
  }
  await db.put(OFFLINE_NOTES_STORE, row)
  await pruneOfflineNotes(offlineNotesMaxItems())
}

export async function getOfflineNote(noteId: string): Promise<OfflineNoteEntry | null> {
  if (!isOfflineNotesEnabled()) return null
  const db = await openOfflineDb()
  const row = (await db.get(OFFLINE_NOTES_STORE, noteId)) as OfflineNoteDbRow | null
  if (!row) return null
  return decodeRow(row)
}

export async function listOfflineNotes(): Promise<OfflineNoteEntry[]> {
  if (!isOfflineNotesEnabled()) return []
  const db = await openOfflineDb()
  const all = (await db.getAll(OFFLINE_NOTES_STORE)) as OfflineNoteDbRow[]
  const out: OfflineNoteEntry[] = []
  for (const row of all) {
    const decoded = await decodeRow(row)
    if (decoded) out.push(decoded)
  }
  return out
}

export async function deleteOfflineNote(noteId: string) {
  if (!isOfflineNotesEnabled()) return
  const db = await openOfflineDb()
  await db.delete(OFFLINE_NOTES_STORE, noteId)
}

export async function clearOfflineNotes() {
  try {
    await deleteDB(OFFLINE_DB_NAME)
  } catch {
    // ignore cleanup errors
  }
}

export async function pruneOfflineNotes(maxItems: number) {
  if (!isOfflineNotesEnabled()) return
  const db = await openOfflineDb()
  const all = (await db.getAll(OFFLINE_NOTES_STORE)) as OfflineNoteDbRow[]
  if (all.length <= maxItems) return
  const sorted = all.sort((a, b) => Date.parse(b.cachedAt) - Date.parse(a.cachedAt))
  const toDelete = sorted.slice(maxItems)
  await Promise.all(toDelete.map((item) => db.delete(OFFLINE_NOTES_STORE, item.noteId)))
}
