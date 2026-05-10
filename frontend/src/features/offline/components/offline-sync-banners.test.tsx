import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { OfflineSyncPromptBanner } from './OfflineSyncPromptBanner'
import { OfflineSyncSummaryBanner } from './OfflineSyncSummaryBanner'

describe('offline sync banners', () => {
  it('renders prompt banner and triggers actions', () => {
    const onSyncNow = vi.fn()
    const onReview = vi.fn()
    const onNotNow = vi.fn()
    render(
      <OfflineSyncPromptBanner
        readyCount={3}
        onSyncNow={onSyncNow}
        onReviewDrafts={onReview}
        onNotNow={onNotNow}
      />,
    )
    expect(screen.getByText(/3 offline drafts ready to sync/i)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Sync now' }))
    fireEvent.click(screen.getByRole('button', { name: 'Review drafts' }))
    fireEvent.click(screen.getByRole('button', { name: 'Not now' }))
    expect(onSyncNow).toHaveBeenCalledTimes(1)
    expect(onReview).toHaveBeenCalledTimes(1)
    expect(onNotNow).toHaveBeenCalledTimes(1)
  })

  it('renders syncing and summary states', () => {
    const { rerender } = render(<OfflineSyncSummaryBanner state="syncing" syncingCount={2} summary={null} />)
    expect(screen.getByText(/syncing 2 offline drafts/i)).toBeTruthy()
    rerender(
      <OfflineSyncSummaryBanner
        state="summary"
        syncingCount={0}
        summary={{
          startedAt: '2024-01-01T00:00:00.000Z',
          completedAt: '2024-01-01T00:01:00.000Z',
          attempted: 3,
          synced: 2,
          conflicts: 1,
          failed: 0,
          queued: 0,
          skipped: 1,
          needsUserConsent: false,
          mode: 'auto_safe',
          stopReason: null,
          eligibleCount: 3,
          skippedReasons: {
            conflict: 0,
            failed: 0,
            locked: 0,
            missing_base_etag: 1,
            max_attempts: 0,
            stale_or_too_old: 0,
            session_unavailable: 0,
            network_guardrail: 0,
            encryption_key_unavailable: 0,
            requires_user_review: 0,
            currently_editing: 0,
            syncing: 0,
            synced: 0,
            status_not_eligible: 0,
            batch_limit: 0,
          },
        }}
      />,
    )
    expect(screen.getByText(/2 drafts synced, 1 needs review/i)).toBeTruthy()
  })

  it('renders blocked state', () => {
    render(
      <OfflineSyncSummaryBanner
        state="blocked"
        syncingCount={0}
        summary={{
          startedAt: '2024-01-01T00:00:00.000Z',
          completedAt: '2024-01-01T00:01:00.000Z',
          attempted: 0,
          synced: 0,
          conflicts: 0,
          failed: 0,
          queued: 0,
          skipped: 0,
          needsUserConsent: false,
          mode: 'auto_safe',
          stopReason: 'ENCRYPTION_KEY_UNAVAILABLE',
          eligibleCount: 0,
          skippedReasons: {
            conflict: 0,
            failed: 0,
            locked: 0,
            missing_base_etag: 0,
            max_attempts: 0,
            stale_or_too_old: 0,
            session_unavailable: 0,
            network_guardrail: 0,
            encryption_key_unavailable: 0,
            requires_user_review: 0,
            currently_editing: 0,
            syncing: 0,
            synced: 0,
            status_not_eligible: 0,
            batch_limit: 0,
          },
        }}
      />,
    )
    expect(screen.getByText(/background sync blocked/i)).toBeTruthy()
  })
})
