import type { Note } from '../../shared/types/api'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearOfflineEncryptionKey, createOfflineEncryptionKey, isOfflineCryptoSupported } from './offline-crypto'
import {
  clearOfflineNotes,
  deleteOfflineNote,
  getOfflineNote,
  listOfflineNotes,
  pruneOfflineNotes,
  saveOfflineNote,
} from './offline-note-cache'

type OfflineEntry = {
  noteId: string
  note: Note
  etag: string | null
  cachedAt: string
}

const notesStore = new Map<string, OfflineEntry>()
const draftsStore = new Map<string, unknown>()

vi.mock('idb', () => ({
  openDB: vi.fn(
    async (_name: string, _version: number, opts?: { upgrade?: (db: any, oldVersion: number) => void }) => {
      if (opts?.upgrade) {
        opts.upgrade(
          {
            objectStoreNames: { contains: () => false },
            createObjectStore: () => ({ createIndex: () => {} }),
          },
          0,
        )
      }
      return {
        put: async (storeName: string, value: OfflineEntry | { noteId: string }) => {
          if (storeName === 'notes') notesStore.set(value.noteId, value as OfflineEntry)
          else draftsStore.set(value.noteId, value)
        },
        get: async (storeName: string, key: string) => {
          if (storeName === 'notes') return notesStore.get(key) ?? null
          return draftsStore.get(key) ?? null
        },
        getAll: async (storeName: string) => {
          if (storeName === 'notes') return Array.from(notesStore.values())
          return Array.from(draftsStore.values())
        },
        delete: async (storeName: string, key: string) => {
          if (storeName === 'notes') notesStore.delete(key)
          else draftsStore.delete(key)
        },
        getAllKeys: async (storeName: string) => {
          if (storeName === 'notes') return Array.from(notesStore.keys())
          return Array.from(draftsStore.keys())
        },
      }
    },
  ),
  deleteDB: vi.fn(async () => {
    notesStore.clear()
    draftsStore.clear()
  }),
}))

const baseNote = (id: string): Note => ({
  id,
  workspaceId: 'ws-1',
  notebookId: 'nb-1',
  title: `note-${id}`,
  contentBlocks: [],
  contentSchemaVersion: 1,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
})

describe('offline-note-cache', () => {
  beforeEach(() => {
    notesStore.clear()
    draftsStore.clear()
    clearOfflineEncryptionKey()
    window.__NOTEBOOK_CONFIG__ = {
      ...(window.__NOTEBOOK_CONFIG__ ?? {}),
      OFFLINE_NOTES_ENABLED: true,
      OFFLINE_NOTES_MAX_ITEMS: 2,
      OFFLINE_ENCRYPTION_ENABLED: false,
      OFFLINE_CACHE_ENCRYPTION_ENABLED: false,
    }
  })

  it('saves and gets offline notes', async () => {
    await saveOfflineNote(baseNote('a'), 'etag-a')
    const cached = await getOfflineNote('a')
    expect(cached?.note.id).toBe('a')
    expect(cached?.etag).toBe('etag-a')
  })

  it('prunes older notes and clears cache', async () => {
    await saveOfflineNote(baseNote('a'), null)
    await saveOfflineNote(baseNote('b'), null)
    await saveOfflineNote(baseNote('c'), null)
    await pruneOfflineNotes(2)
    expect((await listOfflineNotes()).length).toBeLessThanOrEqual(2)
    await deleteOfflineNote('b')
    await clearOfflineNotes()
    expect(await listOfflineNotes()).toHaveLength(0)
  })

  it('encrypts cache payload when cache encryption enabled', async () => {
    if (!isOfflineCryptoSupported()) return
    window.__NOTEBOOK_CONFIG__ = {
      ...(window.__NOTEBOOK_CONFIG__ ?? {}),
      OFFLINE_NOTES_ENABLED: true,
      OFFLINE_ENCRYPTION_ENABLED: true,
      OFFLINE_CACHE_ENCRYPTION_ENABLED: true,
    }
    await createOfflineEncryptionKey()
    await saveOfflineNote(baseNote('enc'), 'etag-enc')
    const decoded = await getOfflineNote('enc')
    expect(decoded?.etag).toBe('etag-enc')
    clearOfflineEncryptionKey()
    const locked = await getOfflineNote('enc')
    expect(locked).toBeNull()
  })
})
