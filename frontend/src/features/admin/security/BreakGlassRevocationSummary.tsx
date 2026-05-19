import { Card } from '../../../shared/components/Card'

export function BreakGlassRevocationSummary({
  activeCount,
  revocationUiEnabled,
}: {
  activeCount: number | null
  revocationUiEnabled: boolean
}) {
  return (
    <Card className="text-sm text-slate-700">
      <p className="font-semibold text-slate-900">Revocation & denylist</p>
      <p className="mt-1">
        Session revocation UI: {revocationUiEnabled ? 'enabled' : 'disabled (feature flag)'}
      </p>
      <p className="mt-1">
        Active sessions (aggregate): {activeCount === null ? '—' : activeCount}
      </p>
      <p className="mt-2 text-xs text-slate-500">
        No bearer tokens, JWTs, or emergency credentials are displayed on this screen.
      </p>
    </Card>
  )
}
