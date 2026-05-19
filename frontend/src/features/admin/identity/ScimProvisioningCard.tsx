import { Card } from '../../../shared/components/Card'
import type { ScimCompatibilityStatus } from '../enterprise/scim-diagnostics-api'

export function ScimProvisioningCard({ status }: { status: ScimCompatibilityStatus }) {
  const p = status.scimProvider
  return (
    <Card className="space-y-2 text-sm">
      <h3 className="font-semibold text-slate-900">Provider compatibility</h3>
      <p className="text-slate-600">
        Type <span className="font-mono text-xs">{p.type}</span> · last sync{' '}
        <span className="font-mono text-xs">{p.lastSyncStatus}</span>
      </p>
      <ul className="grid gap-1 text-xs text-slate-700 sm:grid-cols-2">
        <li>Delta sync: {p.deltaSyncEnabled ? `yes (${p.deltaSyncMode})` : 'no'}</li>
        <li>Nested groups: {p.nestedGroupsSupported ? 'supported' : 'not supported'}</li>
        <li>Patch: {p.patchSupported ? 'yes' : 'no'}</li>
        <li>Max page size: {p.maxPageSize}</li>
      </ul>
      {p.warnings.length > 0 ? (
        <ul className="mt-2 space-y-1 text-xs text-amber-800">
          {p.warnings.map((w) => (
            <li key={w}>⚠ {w}</li>
          ))}
        </ul>
      ) : null}
    </Card>
  )
}
