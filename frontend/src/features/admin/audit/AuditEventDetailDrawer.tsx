import { maskSensitiveMetadata } from './metadata-mask'
import type { AuditEvent } from './types'

export function AuditEventDetailDrawer({
  event,
  onClose,
}: {
  event: AuditEvent | null
  onClose: () => void
}) {
  if (!event) return null

  const safeMeta = maskSensitiveMetadata(event.metadata ?? {})

  return (
    <aside
      className="fixed inset-y-0 right-0 z-40 w-full max-w-md border-l border-slate-200 bg-white p-4 shadow-lg"
      data-testid="audit-event-detail-drawer"
      role="dialog"
      aria-label="Audit event detail"
    >
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-900">Event detail</h3>
        <button type="button" className="text-sm text-primary-600" onClick={onClose}>
          Close
        </button>
      </div>
      <dl className="mt-4 space-y-2 text-sm">
        <div>
          <dt className="text-slate-500">Type</dt>
          <dd className="font-mono text-xs">{event.eventType}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Source</dt>
          <dd>{event.source}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Request ID</dt>
          <dd className="font-mono text-xs">{event.requestId ?? '—'}</dd>
        </div>
      </dl>
      <p className="mt-4 text-xs font-semibold uppercase text-slate-500">Sanitized metadata</p>
      <pre className="mt-1 max-h-64 overflow-auto rounded bg-slate-50 p-2 text-[11px] text-slate-800">
        {JSON.stringify(safeMeta, null, 2)}
      </pre>
      <p className="mt-2 text-xs text-slate-500">
        Sensitive keys are masked. Raw provider payloads and secrets are never shown.
      </p>
    </aside>
  )
}
