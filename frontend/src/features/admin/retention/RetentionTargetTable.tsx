import { Card } from '../../../shared/components/Card'
import type { RetentionPlanTarget } from '../notification-retention-api'
import { RetentionWarningChip } from './RetentionWarningChip'

type RetentionTargetRow = {
  target: string
  retention: string
  eligibleCount: number
  cutoff: string
  oldestEligibleAt: string | null
  blockedByLegalHold?: boolean
  activeHoldKeys?: string[]
  purgeableCount?: number
  targetWarnings?: string[]
  status?: string
}

function toRow(t: RetentionPlanTarget): RetentionTargetRow {
  return {
    target: t.target,
    retention: t.retention,
    eligibleCount: t.eligibleCount,
    cutoff: t.cutoff,
    oldestEligibleAt: t.oldestEligibleAt,
    blockedByLegalHold: t.blockedByLegalHold,
    activeHoldKeys: t.activeHoldKeys,
    purgeableCount: t.purgeableCount,
    targetWarnings: t.targetWarnings,
  }
}

export function RetentionTargetTable({
  targets,
  showStatus = false,
}: {
  targets: RetentionPlanTarget[] | RetentionTargetRow[]
  showStatus?: boolean
}) {
  const rows = targets.map((t) => ('retention' in t && 'eligibleCount' in t ? toRow(t as RetentionPlanTarget) : (t as RetentionTargetRow)))

  return (
    <Card className="overflow-x-auto p-0" data-testid="retention-target-table">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2">Target</th>
            {showStatus ? <th className="px-3 py-2">Status</th> : null}
            <th className="px-3 py-2">Retention</th>
            <th className="px-3 py-2">Eligible</th>
            <th className="px-3 py-2">Cutoff (UTC)</th>
            <th className="px-3 py-2">Oldest eligible</th>
            <th className="px-3 py-2">Legal hold</th>
            <th className="px-3 py-2">Purgeable</th>
            <th className="px-3 py-2">Warnings</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t) => (
            <tr key={t.target} className="border-b border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{t.target}</td>
              {showStatus ? (
                <td className="px-3 py-2 text-xs">{t.status ?? '—'}</td>
              ) : null}
              <td className="px-3 py-2">{t.retention}</td>
              <td className="px-3 py-2">{t.eligibleCount}</td>
              <td className="px-3 py-2 text-xs text-slate-600">{t.cutoff}</td>
              <td className="px-3 py-2 text-xs text-slate-600">{t.oldestEligibleAt ?? '—'}</td>
              <td className="px-3 py-2 text-xs">
                {t.blockedByLegalHold
                  ? `Blocked (${(t.activeHoldKeys ?? []).join(', ') || 'hold'})`
                  : '—'}
              </td>
              <td className="px-3 py-2">{t.purgeableCount ?? t.eligibleCount}</td>
              <td className="px-3 py-2">
                <div className="flex flex-wrap gap-1">
                  {(t.targetWarnings ?? []).map((w) => (
                    <RetentionWarningChip key={w} code={w} />
                  ))}
                  {(t.targetWarnings ?? []).length === 0 ? '—' : null}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  )
}
