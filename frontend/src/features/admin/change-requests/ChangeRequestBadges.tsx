const statusStyles: Record<string, string> = {
  PENDING: 'bg-amber-100 text-amber-900',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-red-100 text-red-900',
  CANCELLED: 'bg-slate-100 text-slate-600',
}

export function ChangeRequestStatusBadge({ status }: { status: string }) {
  const key = status.toUpperCase()
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase ${statusStyles[key] ?? 'bg-slate-100 text-slate-700'}`}>
      {status}
    </span>
  )
}

export function SeverityBadge({ severity }: { severity: string }) {
  const s = (severity || '—').toUpperCase()
  const cls =
    s === 'HIGH'
      ? 'bg-red-100 text-red-900'
      : s === 'MEDIUM'
        ? 'bg-amber-100 text-amber-900'
        : s === 'LOW'
          ? 'bg-slate-100 text-slate-700'
          : 'bg-slate-100 text-slate-600'
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium ${cls}`}>{severity || '—'}</span>
  )
}

export function OperationTypeBadge({ operationType }: { operationType: string }) {
  return (
    <span className="inline-flex max-w-[12rem] truncate rounded bg-slate-100 px-2 py-0.5 font-mono text-[10px] text-slate-800" title={operationType}>
      {operationType}
    </span>
  )
}

export function GitOpsStateBadge({ state }: { state: string }) {
  const labels: Record<string, string> = {
    not_started: 'GitOps: not started',
    dry_run_required: 'Dry-run required',
    ready: 'Ready for PR',
    creating: 'Creating PR…',
    created: 'PR created',
    failed: 'PR failed',
    disabled: 'GitOps off',
    blocked: 'Not approved',
  }
  const cls =
    state === 'created'
      ? 'bg-sky-100 text-sky-900'
      : state === 'failed'
        ? 'bg-red-100 text-red-900'
        : state === 'disabled' || state === 'blocked'
          ? 'bg-slate-100 text-slate-600'
          : 'bg-violet-100 text-violet-900'
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium ${cls}`}>
      {labels[state] ?? state}
    </span>
  )
}
