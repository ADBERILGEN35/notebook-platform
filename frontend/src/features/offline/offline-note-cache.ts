import { deleteDB } from 'idb'
import type { Note } from '../../shared/types/api'
import { isOfflineNotesEnabled, offlineNotesMaxItems } from '../../shared/config/offline-feature-flags'
import { OFFLINE_DB_NAME, OFFLINE_NOTES_STORE, openOfflineDb } from './offline-db'

export type OfflineNoteEntry = {
  noteId: string
  note: Note
  etag: string | null
  cachedAt: string
}

export async function saveOfflineNote(note: Note, etag: string | null) {
  if (!isOfflineNotesEnabled()) return
  const db = await openOfflineDb()
  await db.put(OFFLINE_NOTES_STORE, {
    noteId: note.id,
    note,
    etag,
    cachedAt: new Date().toISOString(),
  } as OfflineNoteEntry)
  await pruneOfflineNotes(offlineNotesMaxItems())
}

export async function getOfflineNote(noteId: string): Promise<OfflineNoteEntry | null> {
  if (!isOfflineNotesEnabled()) return null
  const db = await openOfflineDb()
  return (await db.get(OFFLINE_NOTES_STORE, noteId)) ?? null
}

export async function listOfflineNotes(): Promise<OfflineNoteEntry[]> {
  if (!isOfflineNotesEnabled()) return []
  const db = await openOfflineDb()
  return await db.getAll(OFFLINE_NOTES_STORE)
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
  const all = (await db.getAll(OFFLINE_NOTES_STORE)) as OfflineNoteEntry[]
  if (all.length <= maxItems) return
  const sorted = all.sort((a, b) => Date.parse(b.cachedAt) - Date.parse(a.cachedAt))
  const toDelete = sorted.slice(maxItems)
  await Promise.all(toDelete.map((item) => db.delete(OFFLINE_NOTES_STORE, item.noteId)))
}
