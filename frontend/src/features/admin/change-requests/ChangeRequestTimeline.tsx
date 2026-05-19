import type { ChangeRequestItem } from '../enterprise/change-requests-api'
import { maskUserId } from './change-request-utils'

type TimelineEvent = {
  label: string
  at: string | null
  actorId: string | null
  detail?: string
}

function buildEvents(row: ChangeRequestItem): TimelineEvent[] {
  const events: TimelineEvent[] = [
    { label: 'Created', at: row.createdAt, actorId: row.requestedByUserId },
  ]
  if (row.approvedAt) {
    events.push({
      label: 'Approved',
      at: row.approvedAt,
      actorId: row.decidedByUserId,
      detail: row.decisionReason ?? undefined,
    })
  }
  if (row.rejectedAt) {
    events.push({
      label: 'Rejected',
      at: row.rejectedAt,
      actorId: row.decidedByUserId,
      detail: row.decisionReason ?? undefined,
    })
  }
  if (row.decidedAt && !row.approvedAt && !row.rejectedAt) {
    events.push({ label: 'Decided', at: row.decidedAt, actorId: row.decidedByUserId })
  }
  return events
}

export function ChangeRequestTimeline({ row }: { row: ChangeRequestItem }) {
  const events = buildEvents(row)
  return (
    <ol className="relative border-l border-slate-200 pl-4 text-sm" data-testid="change-request-timeline">
      {events.map((ev) => (
        <li key={`${ev.label}-${ev.at}`} className="mb-4 ml-1">
          <span className="absolute -left-1.5 mt-1.5 h-2.5 w-2.5 rounded-full border border-white bg-primary-600" />
          <p className="font-medium text-slate-900">{ev.label}</p>
          <p className="text-xs text-slate-600">
            {ev.at ? new Date(ev.at).toLocaleString() : '—'} · actor {maskUserId(ev.actorId)}
          </p>
          {ev.detail ? <p className="mt-1 text-xs text-slate-700">{ev.detail}</p> : null}
        </li>
      ))}
    </ol>
  )
}
