import { Link } from 'react-router-dom'
import { ResponsiveTableShell } from '../../../shared/components/ResponsiveTableShell'
import type { DeadLetterListItem } from '../notification-dead-letter-api'
import { DuplicateRiskBadge } from './DuplicateRiskBadge'
import { maskRecipientHash } from './notification-ops-utils'

type DeadLetterEventTableProps = {
  items: DeadLetterListItem[]
  duplicateRiskById?: Record<string, string>
  showRequeueLink?: boolean
}

export function DeadLetterEventTable({ items, duplicateRiskById, showRequeueLink }: DeadLetterEventTableProps) {
  return (
    <ResponsiveTableShell label="Dead-letter events" testId="dead-letter-event-table">
      <table className="min-w-full text-left text-xs">
        <thead className="border-b bg-slate-50 text-slate-600">
          <tr>
            <th className="px-3 py-2">Event</th>
            <th className="px-3 py-2">Source</th>
            <th className="px-3 py-2">Recipient</th>
            <th className="px-3 py-2">Attempts</th>
            <th className="px-3 py-2">Requeues</th>
            <th className="px-3 py-2">Failure</th>
            <th className="px-3 py-2">Eligibility</th>
            <th className="px-3 py-2">Actions</th>
          </tr>
        </thead>
        <tbody>
          {items.length === 0 ? (
            <tr>
              <td colSpan={8} className="px-3 py-4 text-slate-500">
                No dead-letter events in this page.
              </td>
            </tr>
          ) : (
            items.map((row) => (
              <tr key={row.id} className="border-b border-slate-100">
                <td className="px-3 py-2 font-mono">{row.eventType}</td>
                <td className="px-3 py-2">{row.source}</td>
                <td className="px-3 py-2 font-mono">{maskRecipientHash(row.recipientUserIdHash)}</td>
                <td className="px-3 py-2">{row.attemptCount}</td>
                <td className="px-3 py-2">{row.requeueCount}</td>
                <td className="max-w-xs truncate px-3 py-2" title={row.lastErrorSummary}>
                  <span className="font-mono text-slate-700">{row.lastErrorCode}</span> {row.lastErrorSummary}
                </td>
                <td className="px-3 py-2">
                  {duplicateRiskById?.[row.id] ? (
                    <DuplicateRiskBadge risk={duplicateRiskById[row.id]} />
                  ) : (
                    <span className="text-slate-500">—</span>
                  )}
                </td>
                <td className="space-x-2 whitespace-nowrap px-3 py-2">
                  <Link className="text-primary-700 underline" to={`/app/admin/notifications/dead-letter/${row.id}`}>
                    Details
                  </Link>
                  {showRequeueLink ? (
                    <Link
                      className="text-primary-700 underline"
                      to={`/app/admin/notifications/dead-letter/${row.id}/requeue`}
                    >
                      Requeue
                    </Link>
                  ) : null}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </ResponsiveTableShell>
  )
}
