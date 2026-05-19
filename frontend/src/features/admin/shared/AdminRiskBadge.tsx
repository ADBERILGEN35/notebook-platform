import type { WarningSeverity } from '../enterprise/enterprise-schema'

const styles: Record<WarningSeverity, string> = {
  INFO: 'bg-slate-100 text-slate-700',
  WARNING: 'bg-amber-100 text-amber-900',
  CRITICAL: 'bg-red-100 text-red-900',
}

export function AdminRiskBadge({ severity, label }: { severity: WarningSeverity; label?: string }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium ${styles[severity]}`}>
      {label ?? severity}
    </span>
  )
}
