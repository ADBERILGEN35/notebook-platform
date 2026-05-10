import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { NoteBlock } from '../../shared/types/api'
import { ApiError } from '../../shared/api/api-client'
import { analyzeNoteConflict } from '../notes/utils/blocknote-merge'
import { createNoteSaveSnapshot } from '../notes/utils/note-save-snapshot'
import {
  clearOfflineEncryptionKey,
  createOfflineEncryptionKey,
  decryptJson,
  encryptJson,
  isOfflineCryptoSupported,
} from './offline-crypto'
import {
  clearOfflineDrafts,
  deleteOfflineDraft,
  getOfflineDraft,
  listOfflineDrafts,
  listPendingDrafts,
  markDraftQueued,
  markDraftSynced,
  pruneOfflineDrafts,
  saveOfflineDraft,
} from './offline-note-drafts'
import { draftStatusAfterSyncPolicy, mapHttpErrorToSyncPolicy } from './offline-sync-policy'

const notesStore = new Map<string, unknown>()
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
        put: async (storeName: string, value: { noteId: string }) => {
          if (storeName === 'notes') notesStore.set(value.noteId, value)
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

const paragraph = (id: string, props: Record<string, unknown> = {}): NoteBlock => ({
  id,
  type: 'paragraph',
  content: [],
  props,
  children: [],
})

const draftPayload = (noteId: string) => ({
  noteId,
  workspaceId: 'ws',
  notebookId: 'nb',
  baseEtag: 'e1',
  baseUpdatedAt: '2020-01-01T00:00:00.000Z',
  baseSnapshot: { title: 'B', contentBlocks: [paragraph('1', { v: 1 })] },
  localSnapshot: { title: 'L', contentBlocks: [paragraph('1', { v: 1 })] },
})

describe('offline-sync policy', () => {
  it('maps 412 to conflict', () => {
    const err = new ApiError({
      timestamp: '',
      status: 412,
      errorCode: 'NOTE_CONFLICT',
      message: 'conflict',
      path: '',
    })
    expect(mapHttpErrorToSyncPolicy(err)).toEqual({
      outcome: 'conflict',
      reason: 'NOTE_CONFLICT',
    })
    expect(draftStatusAfterSyncPolicy(mapHttpErrorToSyncPolicy(err))).toBe('CONFLICT')
  })

  it('maps 404 to not_found failed state', () => {
    const err = new ApiError({
      timestamp: '',
      status: 404,
      errorCode: 'NOTE_NOT_FOUND',
      message: 'missing',
      path: '',
    })
    const p = mapHttpErrorToSyncPolicy(err)
    expect(p).toEqual({ outcome: 'not_found', reason: 'NOTE_NOT_FOUND' })
    expect(draftStatusAfterSyncPolicy(p)).toBe('FAILED')
  })

  it('maps 403 to non-requeue failed', () => {
    const err = new ApiError({
      timestamp: '',
      status: 403,
      errorCode: 'FORBIDDEN',
      message: 'no',
      path: '',
    })
    const p = mapHttpErrorToSyncPolicy(err)
    expect(p).toEqual({ outcome: 'failed', reason: 'FORBIDDEN', requeue: false })
    expect(draftStatusAfterSyncPolicy(p)).toBe('FAILED')
  })

  it('maps 503 to requeue', () => {
    const err = new ApiError({
      timestamp: '',
      status: 503,
      errorCode: 'UNAVAILABLE',
      message: 'busy',
      path: '',
    })
    const p = mapHttpErrorToSyncPolicy(err)
    expect(p).toEqual({ outcome: 'failed', reason: 'UNAVAILABLE', requeue: true })
    expect(draftStatusAfterSyncPolicy(p)).toBe('QUEUED')
  })

  it('maps unknown errors to requeue', () => {
    const p = mapHttpErrorToSyncPolicy(new Error('network'))
    expect(p).toEqual({ outcome: 'failed', reason: 'NETWORK_OR_UNKNOWN', requeue: true })
    expect(draftStatusAfterSyncPolicy(p)).toBe('QUEUED')
  })
})

describe('offline-note-drafts (IndexedDB foundation)', () => {
  beforeEach(() => {
    draftsStore.clear()
    notesStore.clear()
    clearOfflineEncryptionKey()
    window.__NOTEBOOK_CONFIG__ = {
      ...(window.__NOTEBOOK_CONFIG__ ?? {}),
      OFFLINE_NOTES_ENABLED: true,
      OFFLINE_EDIT_ENABLED: true,
      OFFLINE_EDIT_MAX_DRAFTS: 50,
      OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS: 7,
      OFFLINE_ENCRYPTION_ENABLED: false,
      OFFLINE_DRAFT_ENCRYPTION_REQUIRED: false,
    }
  })

  it('returns null from save when offline edit disabled', async () => {
    window.__NOTEBOOK_CONFIG__ = {
      ...(window.__NOTEBOOK_CONFIG__ ?? {}),
      OFFLINE_NOTES_ENABLED: true,
      OFFLINE_EDIT_ENABLED: false,
    }
    expect(await saveOfflineDraft(draftPayload('n1'))).toBeNull()
  })

  it('persists one draft per note and reuses draftId', async () => {
    const a = await saveOfflineDraft(draftPayload('n1'))
    const b = await saveOfflineDraft({
      ...draftPayload('n1'),
      localSnapshot: { title: 'L2', contentBlocks: [] },
    })
    expect(a?.draftId).toBe(b?.draftId)
    const row = await getOfflineDraft('n1')
    expect(row?.localSnapshot.title).toBe('L2')
  })

  it('lists pending drafts excluding SYNCED', async () => {
    await saveOfflineDraft(draftPayload('a'))
    await saveOfflineDraft(draftPayload('b'))
    await markDraftSynced('b')
    const pending = await listPendingDrafts()
    expect(pending.map((p) => p.noteId).sort()).toEqual(['a'])
  })

  it('markDraftQueued sets status', async () => {
    await saveOfflineDraft(draftPayload('x'))
    await markDraftQueued('x')
    const row = await getOfflineDraft('x')
    expect(row?.status).toBe('QUEUED')
    expect(row?.queuedAt).toBeTruthy()
  })

  it('prunes by max count', async () => {
    vi.useFakeTimers()
    window.__NOTEBOOK_CONFIG__ = {
      ...(window.__NOTEBOOK_CONFIG__ ?? {}),
      OFFLINE_NOTES_ENABLED: true,
      OFFLINE_EDIT_ENABLED: true,
      OFFLINE_EDIT_MAX_DRAFTS: 2,
      OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS: 7,
    }
    vi.setSystemTime(new Date('2024-01-01T00:00:00.000Z'))
    await saveOfflineDraft(draftPayload('a'))
    vi.setSystemTime(new Date('2024-01-02T00:00:00.000Z'))
    await saveOfflineDraft(draftPayload('b'))
    vi.setSystemTime(new Date('2024-01-03T00:00:00.000Z'))
    await saveOfflineDraft(draftPayload('c'))
    await pruneOfflineDrafts(2, 7)
    vi.useRealTimers()
    const ids = (await listOfflineDrafts()).map((d) => d.noteId).sort()
    expect(ids).toEqual(['b', 'c'])
  })

  it('prunes stale by age', async () => {
    await saveOfflineDraft(draftPayload('stale'))
    const dbRow = (await getOfflineDraft('stale'))!
    dbRow.lastEditedAt = '2000-01-01T00:00:00.000Z'
    draftsStore.set('stale', dbRow)
    await pruneOfflineDrafts(50, 7)
    expect(await getOfflineDraft('stale')).toBeNull()
  })

  it('clearOfflineDrafts removes rows', async () => {
    await saveOfflineDraft(draftPayload('z'))
    await clearOfflineDrafts()
    expect(await listOfflineDrafts()).toHaveLength(0)
  })

  it('deleteOfflineDraft removes one row', async () => {
    await saveOfflineDraft(draftPayload('d'))
    await deleteOfflineDraft('d')
    expect(await getOfflineDraft('d')).toBeNull()
  })

  it('saves encrypted draft and cannot read without key', async () => {
    if (!isOfflineCryptoSupported()) return
    window.__NOTEBOOK_CONFIG__ = {
      ...(window.__NOTEBOOK_CONFIG__ ?? {}),
      OFFLINE_NOTES_ENABLED: true,
      OFFLINE_EDIT_ENABLED: true,
      OFFLINE_ENCRYPTION_ENABLED: true,
      OFFLINE_DRAFT_ENCRYPTION_REQUIRED: true,
    }
    await createOfflineEncryptionKey()
    await saveOfflineDraft(draftPayload('enc1'))
    const withKey = await getOfflineDraft('enc1')
    expect(withKey?.localSnapshot.title).toBe('L')
    clearOfflineEncryptionKey()
    const withoutKey = await getOfflineDraft('enc1')
    expect(withoutKey).toBeNull()
  })

  it('clears plaintext draft rows when encryption required', async () => {
    window.__NOTEBOOK_CONFIG__ = {
      ...(window.__NOTEBOOK_CONFIG__ ?? {}),
      OFFLINE_NOTES_ENABLED: true,
      OFFLINE_EDIT_ENABLED: true,
      OFFLINE_ENCRYPTION_ENABLED: true,
      OFFLINE_DRAFT_ENCRYPTION_REQUIRED: true,
    }
    draftsStore.set('plain', {
      draftId: 'x',
      noteId: 'plain',
      workspaceId: 'ws',
      notebookId: 'nb',
      baseEtag: 'e1',
      baseUpdatedAt: '2020-01-01T00:00:00.000Z',
      baseSnapshot: { title: 'B', contentBlocks: [] },
      localSnapshot: { title: 'L', contentBlocks: [] },
      status: 'DRAFT',
      lastEditedAt: '2020-01-01T00:00:00.000Z',
      queuedAt: null,
      syncedAt: null,
      conflictReason: null,
      attemptCount: 0,
      lastError: null,
    })
    expect(await getOfflineDraft('plain')).toBeNull()
    expect(draftsStore.has('plain')).toBe(false)
  })
})

describe('offline-crypto', () => {
  beforeEach(() => {
    clearOfflineEncryptionKey()
  })

  it('encrypt/decrypt roundtrip with AES-GCM', async () => {
    if (!isOfflineCryptoSupported()) return
    await createOfflineEncryptionKey()
    const encrypted = await encryptJson({ hello: 'world' })
    const decrypted = await decryptJson<{ hello: string }>(encrypted)
    expect(decrypted.hello).toBe('world')
  })

  it('uses different IV for each encryption', async () => {
    if (!isOfflineCryptoSupported()) return
    await createOfflineEncryptionKey()
    const a = await encryptJson({ v: 1 })
    const b = await encryptJson({ v: 1 })
    expect(a.iv).not.toBe(b.iv)
  })

  it('fails decrypt when key is missing', async () => {
    if (!isOfflineCryptoSupported()) return
    await createOfflineEncryptionKey()
    const encrypted = await encryptJson({ v: 1 })
    clearOfflineEncryptionKey()
    await expect(decryptJson(encrypted)).rejects.toBeTruthy()
  })
})

describe('Faz 66 integration (offline conflict analysis)', () => {
  it('analyzeNoteConflict yields suggestion for disjoint edits (draft sync preview)', () => {
    const base = createNoteSaveSnapshot('T', [paragraph('a', { x: 1 }), paragraph('b', { y: 1 })])
    const local = createNoteSaveSnapshot('T', [paragraph('a', { x: 2 }), paragraph('b', { y: 1 })])
    const remote = createNoteSaveSnapshot('T', [paragraph('a', { x: 1 }), paragraph('b', { y: 2 })])
    const analysis = analyzeNoteConflict(base, local, remote)
    expect(analysis.suggestion).not.toBeNull()
    expect(analysis.conflicts).toHaveLength(0)
  })
})
