import { Button } from '../../../shared/components/Button'

type Props = {
  readyCount: number
  onSyncNow: () => void
  onReviewDrafts: () => void
  onNotNow: () => void
}

export function OfflineSyncPromptBanner({ readyCount, onSyncNow, onReviewDrafts, onNotNow }: Props) {
  return (
    <div className="mb-3 rounded border border-indigo-300 bg-indigo-50 p-3 text-sm text-indigo-900" data-testid="offline-sync-prompt-banner">
      <p>You have {readyCount} offline drafts ready to sync.</p>
      <div className="mt-2 flex flex-wrap gap-2">
        <Button type="button" onClick={onSyncNow}>
          Sync now
        </Button>
        <Button type="button" onClick={onReviewDrafts}>
          Review drafts
        </Button>
        <Button type="button" onClick={onNotNow}>
          Not now
        </Button>
      </div>
    </div>
  )
}
