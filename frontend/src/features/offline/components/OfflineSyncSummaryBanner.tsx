import type { BackgroundSyncSummary } from '../offline-background-sync-types'

type Props = {
  state: 'syncing' | 'summary' | 'blocked'
  syncingCount?: number
  summary?: BackgroundSyncSummary | null
}

export function OfflineSyncSummaryBanner({ state, syncingCount = 0, summary }: Props) {
  if (state === 'syncing') {
    return (
      <div className="mb-3 rounded border border-blue-300 bg-blue-50 p-3 text-sm text-blue-900" data-testid="offline-sync-syncing-banner">
        Syncing {syncingCount} offline drafts...
      </div>
    )
  }
  if (state === 'blocked' && summary) {
    return (
      <div className="mb-3 rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" data-testid="offline-sync-blocked-banner">
        Background sync blocked: {summary.stopReason ?? 'unknown'}.
      </div>
    )
  }
  if (state === 'summary' && summary) {
    return (
      <div className="mb-3 rounded border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-900" data-testid="offline-sync-summary-banner">
        {summary.synced} drafts synced, {summary.conflicts + summary.failed} needs review.
      </div>
    )
  }
  return null
}
