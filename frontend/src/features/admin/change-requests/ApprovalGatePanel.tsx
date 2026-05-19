import type { ChangeRequestItem } from '../enterprise/change-requests-api'
import { isHighSeverity, rowSeverity } from './change-request-utils'
import { SeverityBadge } from './ChangeRequestBadges'

export function ApprovalGatePanel({ row }: { row: ChangeRequestItem }) {
  const high = isHighSeverity(row)
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950" data-testid="approval-gate-panel">
      <p className="font-semibold">Approval & apply gate</p>
      <p className="mt-1 text-xs">
        Approval records intent only — no runtime mutation. Apply via GitOps PR or runbook after merge.
      </p>
      <ul className="mt-2 space-y-1 text-xs">
        <li className="flex items-center gap-2">
          Severity: <SeverityBadge severity={rowSeverity(row)} />
        </li>
        {high ? (
          <li className="font-medium text-red-900">HIGH severity — CONFIRM required for approve / prod GitOps PR.</li>
        ) : null}
        <li>Four-eyes: requester cannot approve their own pending request.</li>
        <li>Admin MFA may be required server-side for approve / GitOps actions.</li>
      </ul>
    </div>
  )
}
