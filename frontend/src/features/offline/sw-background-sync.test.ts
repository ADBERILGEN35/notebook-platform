import { beforeEach, describe, expect, it, vi } from 'vitest'
import { selectEligibleDraftsForSwBackgroundSync } from './sw-background-sync-policy'
import { registerSwBackgroundSync, runSwBackgroundSyncDryRun } from './sw-background-sync-registration'

const listOfflineDraftOverviewMock = vi.hoisted(() => vi.fn())
const setSwBackgroundSyncSummaryMock = vi.hoisted(() => vi.fn())

vi.mock('../../shared/config/offline-feature-flags', () => ({
  isSwBackgroundSyncEnabled: () => true,
  isSwBackgroundSyncDryRunOnly: () => true,
  isSwBackgroundSyncRegisterEnabled: () => false,
  swBackgroundSyncMaxBatch: () => 3,
  offlineSyncMaxAttempts: () => 5,
}))

vi.mock('./offline-note-drafts', () => ({
  listOfflineDraftOverview: listOfflineDraftOverviewMock,
}))

vi.mock('./sw-background-sync-diagnostics', () => ({
  setSwBackgroundSyncSummary: setSwBackgroundSyncSummaryMock,
}))

describe('sw-background-sync policy', () => {
  const draft = {
    noteId: 'n1',
    draftId: 'd1',
    status: 'QUEUED' as const,
    attemptCount: 0,
    locked: false,
    baseEtag: 'etag-1',
  }

  it('disables selection on unsupported browsers', () => {
    const out = selectEligibleDraftsForSwBackgroundSync([draft], { browserSupported: false })
    expect(out.eligible).toHaveLength(0)
    expect(out.skipped[0]?.reason).toBe('browser_unsupported')
  })

  it('selects only DRAFT and QUEUED drafts with base etags under max attempts', () => {
    const out = selectEligibleDraftsForSwBackgroundSync(
      [
        draft,
        { ...draft, noteId: 'n2', draftId: 'd2', status: 'DRAFT' as const },
        { ...draft, noteId: 'n3', draftId: 'd3', status: 'CONFLICT' as const },
        { ...draft, noteId: 'n4', draftId: 'd4', status: 'FAILED' as const },
        { ...draft, noteId: 'n5', draftId: 'd5', baseEtag: null },
        { ...draft, noteId: 'n6', draftId: 'd6', attemptCount: 5 },
      ],
      { browserSupported: true, online: true, maxAttempts: 5, maxBatch: 3 },
    )
    expect(out.eligible.map((item) => item.noteId)).toEqual(['n1', 'n2'])
    expect(out.skipped.map((item) => item.reason)).toEqual([
      'conflict',
      'failed',
      'missing_base_etag',
      'max_attempts',
    ])
  })

  it('skips encrypted locked drafts without trying silent unlock', () => {
    const out = selectEligibleDraftsForSwBackgroundSync(
      [{ ...draft, locked: true }],
      { browserSupported: true, online: true },
    )
    expect(out.eligible).toHaveLength(0)
    expect(out.skipped[0]?.reason).toBe('encrypted_key_unavailable')
  })

  it('enforces dry-run batch limits', () => {
    const out = selectEligibleDraftsForSwBackgroundSync(
      [1, 2, 3, 4].map((n) => ({ ...draft, noteId: `n${n}`, draftId: `d${n}` })),
      { browserSupported: true, online: true, maxBatch: 2 },
    )
    expect(out.eligible).toHaveLength(2)
    expect(out.skipped[0]?.reason).toBe('batch_limit')
  })
})

describe('sw-background-sync registration and dry-run', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listOfflineDraftOverviewMock.mockResolvedValue([])
    Object.defineProperty(window.navigator, 'onLine', { value: true, configurable: true })
  })

  it('does not register when register flag is disabled', async () => {
    const out = await registerSwBackgroundSync()
    expect(out.registered).toBe(false)
    expect(out.reason).toBe('register_disabled')
  })

  it('writes diagnostics and does not call remote sync in dry-run', async () => {
    listOfflineDraftOverviewMock.mockResolvedValue([
      {
        noteId: 'n1',
        draftId: 'd1',
        workspaceId: 'ws',
        notebookId: 'nb',
        status: 'QUEUED',
        lastEditedAt: new Date().toISOString(),
        queuedAt: null,
        syncedAt: null,
        attemptCount: 0,
        baseEtag: 'etag-1',
        lastError: null,
        conflictReason: null,
        title: 'Draft',
        locked: false,
      },
      {
        noteId: 'n2',
        draftId: 'd2',
        workspaceId: 'ws',
        notebookId: 'nb',
        status: 'CONFLICT',
        lastEditedAt: new Date().toISOString(),
        queuedAt: null,
        syncedAt: null,
        attemptCount: 0,
        baseEtag: 'etag-2',
        lastError: null,
        conflictReason: 'NOTE_CONFLICT',
        title: 'Conflict',
        locked: false,
      },
      {
        noteId: 'n3',
        draftId: 'd3',
        workspaceId: 'ws',
        notebookId: 'nb',
        status: 'QUEUED',
        lastEditedAt: new Date().toISOString(),
        queuedAt: null,
        syncedAt: null,
        attemptCount: 0,
        baseEtag: null,
        lastError: null,
        conflictReason: null,
        title: '(encrypted draft)',
        locked: true,
      },
    ])

    const summary = await runSwBackgroundSyncDryRun({ browserSupported: true, registered: true })
    expect(summary.mode).toBe('dry-run')
    expect(summary.eligible).toBe(1)
    expect(summary.skipped).toBe(2)
    expect(summary.skipReasons.conflict).toBe(1)
    expect(summary.skipReasons.encrypted_key_unavailable).toBe(1)
    expect(setSwBackgroundSyncSummaryMock).toHaveBeenCalledWith(summary)
  })
})
