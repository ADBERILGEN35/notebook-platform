import { Card } from '../../../shared/components/Card'

export function AdminMetricCard({
  title,
  value,
  hint,
}: {
  title: string
  value: number | string
  hint?: string
}) {
  return (
    <Card className="p-4" data-testid="admin-metric-card">
      <p className="text-xs font-semibold uppercase text-slate-500">{title}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
      {hint ? <p className="mt-1 text-xs text-slate-500">{hint}</p> : null}
    </Card>
  )
}
