import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../shared/api/api-client'
import { syncOfflineDraft } from './offline-sync-service'

const updateNoteMock = vi.hoisted(() => vi.fn())
const getNoteMock = vi.hoisted(() => vi.fn())
const saveOfflineNoteMock = vi.hoisted(() => vi.fn(async () => undefined))
const deleteOfflineDraftMock = vi.hoisted(() => vi.fn(async () => undefined))
const markDraftSyncingMock = vi.hoisted(() => vi.fn(async () => undefined))
const markDraftConflictMock = vi.hoisted(() => vi.fn(async () => undefined))
const markDraftFailedMock = vi.hoisted(() => vi.fn(async () => undefined))
const getOfflineDraftMock = vi.hoisted(() => vi.fn())
const listPendingDraftsMock = vi.hoisted(() => vi.fn(async () => []))
const getRawDraftRowMock = vi.hoisted(() => vi.fn(async () => ({ exists: false, locked: false })))
const recoverStaleSyncingDraftsMock = vi.hoisted(() => vi.fn(async () => 0))
const markSyncAttemptMock = vi.hoisted(() => vi.fn())
const markSyncSuccessMock = vi.hoisted(() => vi.fn())
const updateDraftCountersMock = vi.hoisted(() => vi.fn())

vi.mock('../notes/note-api', () => ({
  updateNote: updateNoteMock,
  getNote: getNoteMock,
  createNote: vi.fn(),
}))
vi.mock('./offline-note-cache', () => ({
  saveOfflineNote: saveOfflineNoteMock,
}))
vi.mock('./offline-note-drafts', () => ({
  deleteOfflineDraft: deleteOfflineDraftMock,
  getOfflineDraft: getOfflineDraftMock,
  getRawDraftRow: getRawDraftRowMock,
  listPendingDrafts: listPendingDraftsMock,
  markDraftSyncing: markDraftSyncingMock,
  markDraftConflict: markDraftConflictMock,
  markDraftFailed: markDraftFailedMock,
  recoverStaleSyncingDrafts: recoverStaleSyncingDraftsMock,
  listOfflineDrafts: vi.fn(async () => []),
}))
vi.mock('../../shared/config/offline-feature-flags', () => ({
  offlineSyncMaxAttempts: () => 5,
  offlineSyncRolloutMode: () => 'manual',
  isOfflineEncryptionEnabled: () => false,
}))
vi.mock('./offline-sync-diagnostics', () => ({
  markSyncAttempt: markSyncAttemptMock,
  markSyncSuccess: markSyncSuccessMock,
  updateDraftCounters: updateDraftCountersMock,
}))

const draft = {
  draftId: 'd1',
  noteId: 'n1',
  workspaceId: 'ws',
  notebookId: 'nb',
  baseEtag: 'e1',
  baseUpdatedAt: '2024-01-01T00:00:00.000Z',
  baseSnapshot: { title: 'base', contentBlocks: [] },
  localSnapshot: { title: 'local', contentBlocks: [] },
  status: 'QUEUED' as const,
  lastEditedAt: '2024-01-01T00:00:00.000Z',
  queuedAt: '2024-01-01T00:00:00.000Z',
  syncedAt: null,
  conflictReason: null,
  attemptCount: 0,
  lastError: null,
}

describe('offline-sync-service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getOfflineDraftMock.mockResolvedValue(draft)
  })

  it('sync success deletes draft and updates offline cache', async () => {
    updateNoteMock.mockResolvedValue({
      note: {
        id: 'n1',
        workspaceId: 'ws',
        notebookId: 'nb',
        title: 'local',
        contentBlocks: [],
        contentSchemaVersion: 1,
        createdAt: '2024-01-01T00:00:00.000Z',
        updatedAt: '2024-01-01T01:00:00.000Z',
      },
      etag: 'e2',
    })
    const out = await syncOfflineDraft('n1')
    expect(out.status).toBe('synced')
    expect(markDraftSyncingMock).toHaveBeenCalledWith('n1')
    expect(saveOfflineNoteMock).toHaveBeenCalled()
    expect(deleteOfflineDraftMock).toHaveBeenCalledWith('n1')
    expect(markSyncAttemptMock).toHaveBeenCalled()
    expect(markSyncSuccessMock).toHaveBeenCalled()
  })

  it('maps 412 to conflict', async () => {
    updateNoteMock.mockRejectedValue(
      new ApiError({ timestamp: '', status: 412, errorCode: 'NOTE_CONFLICT', message: 'x', path: '' }),
    )
    const out = await syncOfflineDraft('n1')
    expect(out.status).toBe('conflict')
    expect(markDraftConflictMock).toHaveBeenCalledWith('n1', 'NOTE_CONFLICT')
  })

  it('maps 503 to queued', async () => {
    updateNoteMock.mockRejectedValue(
      new ApiError({ timestamp: '', status: 503, errorCode: 'UNAVAILABLE', message: 'x', path: '' }),
    )
    const out = await syncOfflineDraft('n1')
    expect(out.status).toBe('queued')
    expect(markDraftFailedMock).toHaveBeenCalledWith('n1', 'UNAVAILABLE', true)
  })

  it('maps 403 to failed', async () => {
    updateNoteMock.mockRejectedValue(
      new ApiError({ timestamp: '', status: 403, errorCode: 'FORBIDDEN', message: 'x', path: '' }),
    )
    const out = await syncOfflineDraft('n1')
    expect(out.status).toBe('failed')
    expect(markDraftFailedMock).toHaveBeenCalledWith('n1', 'FORBIDDEN', false)
  })

  it('maps 404 to not_found', async () => {
    updateNoteMock.mockRejectedValue(
      new ApiError({ timestamp: '', status: 404, errorCode: 'NOTE_NOT_FOUND', message: 'x', path: '' }),
    )
    const out = await syncOfflineDraft('n1')
    expect(out.status).toBe('not_found')
    expect(markDraftFailedMock).toHaveBeenCalledWith('n1', 'NOTE_NOT_FOUND', false)
  })

  it('fails when missing baseEtag', async () => {
    getOfflineDraftMock.mockResolvedValue({ ...draft, baseEtag: null })
    const out = await syncOfflineDraft('n1')
    expect(out.status).toBe('failed')
    expect(markDraftFailedMock).toHaveBeenCalledWith('n1', 'MISSING_BASE_ETAG', false)
  })

  it('returns locked when encrypted row cannot be read', async () => {
    getOfflineDraftMock.mockResolvedValue(null)
    getRawDraftRowMock.mockResolvedValue({ exists: true, locked: true })
    const out = await syncOfflineDraft('n1')
    expect(out.status).toBe('locked')
  })

  it('does not sync conflict row without resolution', async () => {
    getOfflineDraftMock.mockResolvedValue({ ...draft, status: 'CONFLICT' })
    const out = await syncOfflineDraft('n1')
    expect(out.status).toBe('conflict')
    expect(updateNoteMock).not.toHaveBeenCalled()
  })
})
