export function DuplicateRiskBadge({ risk }: { risk: string }) {
  const r = (risk ?? '').toUpperCase()
  const high = r === 'HIGH' || r === 'ELEVATED'
  return (
    <span
      className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-medium ${
        high ? 'bg-amber-100 text-amber-900' : 'bg-slate-100 text-slate-700'
      }`}
      data-testid="duplicate-risk-badge"
    >
      Dup risk: {risk || '—'}
    </span>
  )
}
