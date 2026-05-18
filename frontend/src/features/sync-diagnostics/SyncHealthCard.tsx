import { InlineStatus } from '../../shared/components/InlineStatus'
import { PanelCard } from '../../shared/components/PanelCard'

type SyncHealthCardProps = {
  pending: number
  conflict: number
  failed: number
  syncEnabled: boolean
  backgroundMode: string
}

export function SyncHealthCard({ pending, conflict, failed, syncEnabled, backgroundMode }: SyncHealthCardProps) {
  const tone = conflict > 0 || failed > 0 ? 'warning' : pending > 0 ? 'neutral' : 'success'
  return (
    <PanelCard title="Sync health" subtitle="Aggregate local draft status">
      <InlineStatus
        label={syncEnabled ? `Mode: ${backgroundMode}` : 'Sync disabled'}
        tone={syncEnabled ? tone : 'neutral'}
      />
      <ul className="mt-3 space-y-1 text-body-md text-on-surface-variant">
        <li>Pending drafts: {pending}</li>
        <li>Conflicts: {conflict}</li>
        <li>Failed: {failed}</li>
      </ul>
    </PanelCard>
  )
}
