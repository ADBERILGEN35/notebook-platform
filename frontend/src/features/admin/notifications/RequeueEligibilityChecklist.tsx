import type { RequeueDryRunResponse } from '../notification-dead-letter-api'

export function RequeueEligibilityChecklist({ dryRun }: { dryRun: RequeueDryRunResponse | null }) {
  if (!dryRun) {
    return (
      <ul className="list-inside list-disc text-sm text-slate-600" data-testid="requeue-eligibility-checklist">
        <li>Run dry-run analysis to load eligibility checks.</li>
      </ul>
    )
  }
  const checks = dryRun.checks ?? []
  return (
    <ul className="space-y-1 text-sm" data-testid="requeue-eligibility-checklist">
      {checks.length === 0 ? (
        <li className="text-slate-600">No explicit checks returned; review warnings before confirming.</li>
      ) : (
        checks.map((c) => (
          <li key={c.code} className={c.passed ? 'text-emerald-800' : 'text-amber-900'}>
            {c.passed ? '✓' : '○'} {c.code}
          </li>
        ))
      )}
    </ul>
  )
}
