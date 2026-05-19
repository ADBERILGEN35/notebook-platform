import { AdminHealthCard } from '../shared/AdminHealthCard'
import type { EnterpriseStatusResponse } from '../enterprise/enterprise-schema'

export function BreakGlassStatusCard({ status }: { status: EnterpriseStatusResponse | undefined }) {
  const bg = status?.features.breakGlass
  if (!bg) {
    return (
      <AdminHealthCard title="Break-glass" statusLabel="Unavailable" ok={false} detail="Status not in snapshot." />
    )
  }
  return (
    <AdminHealthCard
      title="Break-glass posture"
      statusLabel={bg.enabled ? 'Enabled' : 'Disabled'}
      ok={bg.enabled && bg.gatewayAllowed}
      detail={`Gateway ${bg.gatewayAllowed ? 'allowed' : 'write blocked'} · pending reviews ${bg.pendingReviewCount} · overdue ${bg.overdueReviewCount}`}
    />
  )
}
