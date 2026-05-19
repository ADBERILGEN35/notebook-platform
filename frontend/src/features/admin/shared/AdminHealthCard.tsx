import { Card } from '../../../shared/components/Card'
import { AdminRiskBadge } from './AdminRiskBadge'
import type { WarningSeverity } from '../enterprise/enterprise-schema'

type AdminHealthCardProps = {
  title: string
  statusLabel: string
  ok: boolean
  detail?: string
  severity?: WarningSeverity
}

export function AdminHealthCard({ title, statusLabel, ok, detail, severity }: AdminHealthCardProps) {
  return (
    <Card className="space-y-2">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
        <span
          className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium ${
            ok ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'
          }`}
        >
          {statusLabel}
        </span>
      </div>
      {detail ? <p className="text-xs text-slate-600">{detail}</p> : null}
      {severity ? <AdminRiskBadge severity={severity} /> : null}
    </Card>
  )
}
