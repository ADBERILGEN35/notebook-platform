import { Card } from '../../../shared/components/Card'
import { sanitizeLegalHoldReason } from '../notifications/notification-ops-utils'

export type LegalHoldDisplay = {
  id: string
  holdKey: string
  scope: string
  status: string
  createdAt: string
  expiresAt?: string | null
  ownerLabel?: string
  reason?: string
  blockedTargets?: string[]
}

export function LegalHoldCard({ hold }: { hold: LegalHoldDisplay }) {
  return (
    <Card className="space-y-2 p-4" data-testid="legal-hold-card">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-mono text-sm font-semibold text-slate-900">{hold.holdKey}</p>
          <p className="text-xs text-slate-500">Scope: {hold.scope}</p>
        </div>
        <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-800">{hold.status}</span>
      </div>
      <dl className="grid gap-1 text-xs text-slate-600 sm:grid-cols-2">
        <div>
          <dt className="text-slate-500">Created</dt>
          <dd>{hold.createdAt}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Expires</dt>
          <dd>{hold.expiresAt ?? '—'}</dd>
        </div>
        {hold.ownerLabel ? (
          <div>
            <dt className="text-slate-500">Owner</dt>
            <dd>{hold.ownerLabel}</dd>
          </div>
        ) : null}
      </dl>
      {hold.reason ? (
        <p className="text-xs text-slate-700">
          <span className="font-medium">Reason:</span> {sanitizeLegalHoldReason(hold.reason)}
        </p>
      ) : null}
      {hold.blockedTargets && hold.blockedTargets.length > 0 ? (
        <p className="text-xs text-amber-900">
          Blocked targets: {hold.blockedTargets.join(', ')}
        </p>
      ) : null}
    </Card>
  )
}
