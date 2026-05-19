import { Card } from '../../../shared/components/Card'
import type { StoredPurgeResult } from './purge-result-storage'

export function PurgeResultSummary({ payload }: { payload: StoredPurgeResult }) {
  const r = payload.result
  return (
    <div className="space-y-4" data-testid="purge-result-summary">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <ResultCard label="Request ID" value={payload.requestId} />
        <ResultCard label="Dry run" value={r.dryRun ? 'Yes' : 'No'} />
        <ResultCard label="Total deleted" value={r.totalDeleted} />
        <ResultCard label="Skipped (legal hold)" value={r.skippedByLegalHold ?? 0} />
      </div>
      <Card className="p-4 text-sm text-slate-700">
        <p>
          <span className="font-medium text-slate-900">Actor:</span> {payload.actorLabel}
        </p>
        <p className="mt-1">
          <span className="font-medium text-slate-900">Recorded:</span> {payload.recordedAt}
        </p>
        <p className="mt-1">
          <span className="font-medium text-slate-900">Target:</span> {r.target}
        </p>
        {r.legalHoldKeysBlocking && r.legalHoldKeysBlocking.length > 0 ? (
          <p className="mt-2 text-amber-900">
            Legal holds blocking: {r.legalHoldKeysBlocking.join(', ')}
          </p>
        ) : null}
      </Card>
      <Card className="overflow-x-auto p-0">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2">Target</th>
              <th className="px-3 py-2">Deleted count</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(r.deletedByTarget).map(([k, v]) => (
              <tr key={k} className="border-b border-slate-100">
                <td className="px-3 py-2 font-mono text-xs">{k}</td>
                <td className="px-3 py-2">{v}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  )
}

function ResultCard({ label, value }: { label: string; value: string | number }) {
  return (
    <Card className="p-3">
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div className="mt-1 text-lg font-semibold text-slate-900">{value}</div>
    </Card>
  )
}
