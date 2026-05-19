import { Card } from '../../../shared/components/Card'
import type { RetentionServiceSummary } from '../platform-retention-api'

const SERVICE_STATUS_TONE: Record<string, string> = {
  READY: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  PARTIAL: 'bg-amber-50 text-amber-800 ring-amber-200',
  INVENTORY_ONLY: 'bg-slate-100 text-slate-700 ring-slate-200',
  DISABLED: 'bg-slate-100 text-slate-700 ring-slate-200',
  UNAVAILABLE: 'bg-rose-50 text-rose-800 ring-rose-200',
  BLOCKED_BY_HOLD: 'bg-rose-50 text-rose-800 ring-rose-200',
  ERROR: 'bg-rose-50 text-rose-800 ring-rose-200',
}

function ServiceStatusBadge({ status }: { status: string }) {
  const style = SERVICE_STATUS_TONE[status] ?? 'bg-slate-100 text-slate-700 ring-slate-200'
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-medium ring-1 ${style}`}>{status}</span>
  )
}

export function RetentionServiceSummaryCard({
  summary,
  generatedAt,
  targetsAnchor = '#platform-retention-dry-run',
}: {
  summary: RetentionServiceSummary
  generatedAt: string
  targetsAnchor?: string
}) {
  return (
    <Card className="space-y-2 p-3" data-testid="retention-service-summary-card">
      <div className="flex items-center justify-between gap-2">
        <div>
          <div className="text-sm font-semibold text-slate-900">{summary.service}</div>
          <div className="text-xs uppercase text-slate-500">{summary.dataClass}</div>
        </div>
        <ServiceStatusBadge status={summary.status} />
      </div>
      <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-slate-600">
        <Metric label="Total" value={summary.totalTargets} />
        <Metric label="Dry-run ready" value={summary.dryRunReadyTargets} />
        <Metric label="Inventory only" value={summary.inventoryOnlyTargets} />
        <Metric label="Blocked by hold" value={summary.blockedTargets} />
        <Metric label="Capped" value={summary.cappedTargets} />
        <Metric label="Warnings" value={summary.warningCount} />
      </dl>
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>{generatedAt}</span>
        <a className="text-slate-700 underline" href={targetsAnchor}>
          View targets
        </a>
      </div>
    </Card>
  )
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-semibold text-slate-900">{value}</dd>
    </div>
  )
}
