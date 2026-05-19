import type { AuditEvent } from './types'

function sourceBadgeClass(source: AuditEvent['source']): string {
  if (source === 'identity') return 'bg-violet-100 text-violet-800'
  if (source === 'workspace') return 'bg-sky-100 text-sky-800'
  return 'bg-slate-100 text-slate-700'
}

export function AuditEventTable({
  events,
  onSelect,
}: {
  events: AuditEvent[]
  onSelect?: (id: string) => void
}) {
  return (
    <div className="overflow-x-auto rounded border border-slate-200 bg-white">
      <table className="min-w-full text-sm" data-testid="audit-event-table">
        <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2">Time</th>
            <th className="px-3 py-2">Type</th>
            <th className="px-3 py-2">Source</th>
            <th className="px-3 py-2">Actor</th>
            <th className="px-3 py-2">Aggregate</th>
          </tr>
        </thead>
        <tbody>
          {events.length === 0 ? (
            <tr>
              <td colSpan={5} className="px-3 py-4 text-slate-500">
                No audit events.
              </td>
            </tr>
          ) : (
            events.map((ev) => (
              <tr
                key={ev.id}
                className="cursor-pointer border-t border-slate-100 hover:bg-slate-50"
                onClick={() => onSelect?.(ev.id)}
              >
                <td className="px-3 py-2 whitespace-nowrap">{ev.createdAt}</td>
                <td className="px-3 py-2 font-mono text-xs">{ev.eventType}</td>
                <td className="px-3 py-2">
                  <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${sourceBadgeClass(ev.source)}`}>
                    {ev.source}
                  </span>
                </td>
                <td className="px-3 py-2 font-mono text-xs">{ev.actorUserId ?? '—'}</td>
                <td className="px-3 py-2 text-xs">
                  {ev.aggregateType}:{ev.aggregateId}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
