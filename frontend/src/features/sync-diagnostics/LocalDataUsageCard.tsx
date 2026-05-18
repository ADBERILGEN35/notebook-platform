import { PanelCard } from '../../shared/components/PanelCard'

type LocalDataUsageCardProps = {
  cachedNotes: number
  pendingDrafts: number
  encryptionActive: boolean
}

export function LocalDataUsageCard({ cachedNotes, pendingDrafts, encryptionActive }: LocalDataUsageCardProps) {
  return (
    <PanelCard title="Local data" subtitle="Aggregate counts only — no secrets stored in UI">
      <ul className="space-y-1 text-body-md text-on-surface-variant">
        <li>Cached notes (this browser): {cachedNotes}</li>
        <li>Pending offline drafts: {pendingDrafts}</li>
        <li>Encryption session key: {encryptionActive ? 'active' : 'not active'}</li>
      </ul>
    </PanelCard>
  )
}
