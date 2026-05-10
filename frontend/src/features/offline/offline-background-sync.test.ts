import { beforeEach, describe, expect, it, vi } from 'vitest'
import { evaluateBackgroundSyncTrigger, selectEligibleDraftsForBackgroundSync } from './offline-background-sync-policy'
import { runForegroundBackgroundSync } from './offline-background-sync-service'

const getOfflineSyncDiagnosticsMock = vi.hoisted(() => vi.fn())
const setBackgroundSyncSummaryMock = vi.hoisted(() => vi.fn())

vi.mock('../../shared/config/offline-feature-flags', () => ({
  isOfflineBackgroundSyncEnabled: () => true,
  offlineBackgroundSyncMode: () => 'auto_safe',
  offlineBackgroundSyncMaxBatch: () => 5,
  offlineBackgroundSyncMinIntervalSeconds: () => 60,
  offlineBackgroundSyncRequireUnmetered: () => false,
  offlineEditMaxDraftAgeDays: () => 7,
  offlineSyncMaxAttempts: () => 5,
}))
vi.mock('./offline-sync-diagnostics', () => ({
  getOfflineSyncDiagnostics: getOfflineSyncDiagnosticsMock,
  setBackgroundSyncSummary: setBackgroundSyncSummaryMock,
}))
vi.mock('./offline-note-drafts', () => ({
  listOfflineDrafts: vi.fn(async () => []),
}))
vi.mock('./offline-sync-service', () => ({
  syncOfflineDraft: vi.fn(),
}))

describe('offline-background-sync policy', () => {
  const baseDraft = {
    draftId: 'd1',
    noteId: 'n1',
    workspaceId: 'ws',
    notebookId: 'nb',
    baseEtag: 'e1',
    baseUpdatedAt: '2024-01-01T00:00:00.000Z',
    baseSnapshot: { title: 'base', contentBlocks: [] },
    localSnapshot: { title: 'local', contentBlocks: [] },
    status: 'QUEUED' as const,
    lastEditedAt: new Date().toISOString(),
    queuedAt: new Date().toISOString(),
    syncedAt: null,
    conflictReason: null,
    attemptCount: 0,
    lastError: null,
  }

  it('selects only safe eligible drafts', () => {
    const out = selectEligibleDraftsForBackgroundSync(
      [
        baseDraft,
        { ...baseDraft, noteId: 'n2', draftId: 'd2', status: 'CONFLICT' as const },
        { ...baseDraft, noteId: 'n3', draftId: 'd3', baseEtag: null },
        { ...baseDraft, noteId: 'n4', draftId: 'd4', attemptCount: 9 },
      ],
      { maxAttempts: 5, maxBatch: 5, maxAgeDays: 7 },
    )
    expect(out.eligible.map((e) => e.noteId)).toEqual(['n1'])
    expect(out.skipped.map((s) => s.noteId)).toEqual(['n2', 'n3', 'n4'])
    expect(out.skipped.map((s) => s.reason)).toEqual(['conflict', 'missing_base_etag', 'max_attempts'])
  })

  it('skips active note id while editing', () => {
    const out = selectEligibleDraftsForBackgroundSync([baseDraft], { activeNoteId: 'n1' })
    expect(out.eligible).toHaveLength(0)
    expect(out.skipped[0]?.reason).toBe('currently_editing')
  })

  it('requires user consent in prompt mode', () => {
    const out = evaluateBackgroundSyncTrigger({
      mode: 'prompt',
      enabled: true,
      isOnline: true,
      authenticated: true,
      encryptionReady: true,
      requireUnmetered: false,
      nowMs: 1000,
      lastAttemptAtMs: null,
      minIntervalSeconds: 60,
    })
    expect(out.needsUserConsent).toBe(true)
    expect(out.canRun).toBe(false)
  })

  it('skips when save-data is active and unmetered is required', () => {
    const out = evaluateBackgroundSyncTrigger({
      mode: 'auto_safe',
      enabled: true,
      isOnline: true,
      authenticated: true,
      encryptionReady: true,
      requireUnmetered: true,
      saveData: true,
      nowMs: 1000,
      lastAttemptAtMs: null,
      minIntervalSeconds: 60,
    })
    expect(out.canRun).toBe(false)
    expect(out.reason).toBe('SAVE_DATA_ENABLED')
  })
})

describe('offline-background-sync service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getOfflineSyncDiagnosticsMock.mockReturnValue({
      draftsPending: 0,
      draftsConflict: 0,
      draftsFailed: 0,
      lastSyncAttemptAt: null,
      syncAttempts: 0,
      syncSuccess: 0,
      lastBackgroundSyncStartedAt: null,
      lastBackgroundSyncCompletedAt: null,
      lastBackgroundSyncMode: null,
      backgroundAttempted: 0,
      backgroundSynced: 0,
      backgroundConflicts: 0,
      backgroundFailed: 0,
      backgroundQueued: 0,
      backgroundSkipped: 0,
      backgroundSkippedReasons: null,
      backgroundStoppedReason: null,
      lastBackgroundSyncResult: null,
    })
    Object.defineProperty(window.navigator, 'onLine', { value: true, configurable: true })
  })

  it('runs eligible batch in auto_safe mode and counts outcomes', async () => {
    const summary = await runForegroundBackgroundSync({
      authenticated: true,
      encryptionReady: true,
      deps: {
        listDrafts: async () => [
          {
            draftId: 'd1',
            noteId: 'n1',
            workspaceId: 'ws',
            notebookId: 'nb',
            baseEtag: 'e1',
            baseUpdatedAt: '2024-01-01T00:00:00.000Z',
            baseSnapshot: { title: 'base', contentBlocks: [] },
            localSnapshot: { title: 'local', contentBlocks: [] },
            status: 'DRAFT',
            lastEditedAt: new Date().toISOString(),
            queuedAt: null,
            syncedAt: null,
            conflictReason: null,
            attemptCount: 0,
            lastError: null,
          },
          {
            draftId: 'd2',
            noteId: 'n2',
            workspaceId: 'ws',
            notebookId: 'nb',
            baseEtag: 'e2',
            baseUpdatedAt: '2024-01-01T00:00:00.000Z',
            baseSnapshot: { title: 'base', contentBlocks: [] },
            localSnapshot: { title: 'local', contentBlocks: [] },
            status: 'QUEUED',
            lastEditedAt: new Date().toISOString(),
            queuedAt: null,
            syncedAt: null,
            conflictReason: null,
            attemptCount: 0,
            lastError: null,
          },
          {
            draftId: 'd3',
            noteId: 'n3',
            workspaceId: 'ws',
            notebookId: 'nb',
            baseEtag: null,
            baseUpdatedAt: '2024-01-01T00:00:00.000Z',
            baseSnapshot: { title: 'base', contentBlocks: [] },
            localSnapshot: { title: 'local', contentBlocks: [] },
            status: 'QUEUED',
            lastEditedAt: new Date().toISOString(),
            queuedAt: null,
            syncedAt: null,
            conflictReason: null,
            attemptCount: 0,
            lastError: null,
          },
        ],
        syncDraft: vi
          .fn()
          .mockResolvedValueOnce({ status: 'synced', noteId: 'n1' })
          .mockResolvedValueOnce({ status: 'conflict', noteId: 'n2', reason: 'NOTE_CONFLICT' }),
      },
    })

    expect(summary.attempted).toBe(2)
    expect(summary.synced).toBe(1)
    expect(summary.conflicts).toBe(1)
    expect(summary.skipped).toBe(1)
    expect(setBackgroundSyncSummaryMock).toHaveBeenCalled()
  })

  it('stops batch on session-related failure', async () => {
    const syncDraft = vi
      .fn()
      .mockResolvedValueOnce({ status: 'failed', noteId: 'n1', reason: 'AUTH_SESSION_EXPIRED' })
      .mockResolvedValueOnce({ status: 'synced', noteId: 'n2' })
    const summary = await runForegroundBackgroundSync({
      authenticated: true,
      encryptionReady: true,
      deps: {
        listDrafts: async () =>
          [1, 2].map((n) => ({
            draftId: `d${n}`,
            noteId: `n${n}`,
            workspaceId: 'ws',
            notebookId: 'nb',
            baseEtag: `e${n}`,
            baseUpdatedAt: '2024-01-01T00:00:00.000Z',
            baseSnapshot: { title: 'base', contentBlocks: [] },
            localSnapshot: { title: 'local', contentBlocks: [] },
            status: 'QUEUED' as const,
            lastEditedAt: new Date().toISOString(),
            queuedAt: null,
            syncedAt: null,
            conflictReason: null,
            attemptCount: 0,
            lastError: null,
          })),
        syncDraft,
      },
    })
    expect(summary.stopReason).toBe('session_expired')
    expect(syncDraft).toHaveBeenCalledTimes(1)
  })

  it('prompt mode does not sync automatically', async () => {
    const syncDraft = vi.fn()
    const summary = await runForegroundBackgroundSync({
      authenticated: true,
      encryptionReady: true,
      modeOverride: 'prompt',
      deps: {
        listDrafts: async () => [
          {
            draftId: 'd1',
            noteId: 'n1',
            workspaceId: 'ws',
            notebookId: 'nb',
            baseEtag: 'e1',
            baseUpdatedAt: '2024-01-01T00:00:00.000Z',
            baseSnapshot: { title: 'base', contentBlocks: [] },
            localSnapshot: { title: 'local', contentBlocks: [] },
            status: 'QUEUED',
            lastEditedAt: new Date().toISOString(),
            queuedAt: null,
            syncedAt: null,
            conflictReason: null,
            attemptCount: 0,
            lastError: null,
          },
        ],
        syncDraft,
      },
    })
    expect(summary.needsUserConsent).toBe(true)
    expect(summary.eligibleCount).toBe(1)
    expect(syncDraft).not.toHaveBeenCalled()
  })

  it('prompt mode syncs when explicitly allowed', async () => {
    const syncDraft = vi.fn().mockResolvedValue({ status: 'synced', noteId: 'n1' })
    const summary = await runForegroundBackgroundSync({
      authenticated: true,
      encryptionReady: true,
      modeOverride: 'prompt',
      allowPromptExecution: true,
      deps: {
        listDrafts: async () => [
          {
            draftId: 'd1',
            noteId: 'n1',
            workspaceId: 'ws',
            notebookId: 'nb',
            baseEtag: 'e1',
            baseUpdatedAt: '2024-01-01T00:00:00.000Z',
            baseSnapshot: { title: 'base', contentBlocks: [] },
            localSnapshot: { title: 'local', contentBlocks: [] },
            status: 'QUEUED',
            lastEditedAt: new Date().toISOString(),
            queuedAt: null,
            syncedAt: null,
            conflictReason: null,
            attemptCount: 0,
            lastError: null,
          },
        ],
        syncDraft,
      },
    })
    expect(summary.synced).toBe(1)
    expect(syncDraft).toHaveBeenCalledTimes(1)
  })
})
