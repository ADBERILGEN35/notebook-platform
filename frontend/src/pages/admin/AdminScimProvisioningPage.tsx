import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../../features/auth/auth-store'
import { PERM_ENTERPRISE_STATUS_READ, hasPlatformPermission } from '../../features/admin/access/admin-permissions'
import {
  fetchScimCompatibilityStatus,
  fetchScimSyncRuns,
} from '../../features/admin/enterprise/scim-diagnostics-api'
import { AdminPageShell } from '../../features/admin/shared/AdminPageShell'
import { ScimProvisioningCard } from '../../features/admin/identity/ScimProvisioningCard'
import { AdminRunbookLink } from '../../features/admin/shared/AdminRunbookLink'
import {
  getAuditApiMode,
  isAdminUiDevOpen,
  isScimCompatibilityDiagnosticsUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { LoadingState } from '../../shared/components/LoadingState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PermissionDenied } from '../../shared/components/PermissionDenied'
import { Card } from '../../shared/components/Card'
import { EmptyState } from '../../shared/components/EmptyState'

export function AdminScimProvisioningPage() {
  const user = useAuthStore((s) => s.user)
  const devOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const canRead = devOpen || hasPlatformPermission(user, PERM_ENTERPRISE_STATUS_READ)
  const flagOn = isScimCompatibilityDiagnosticsUiEnabled()

  const statusQ = useQuery({
    queryKey: ['scim-compatibility'],
    queryFn: fetchScimCompatibilityStatus,
    enabled: canRead && flagOn,
  })
  const runsQ = useQuery({
    queryKey: ['scim-sync-runs'],
    queryFn: fetchScimSyncRuns,
    enabled: canRead && flagOn,
  })

  if (!canRead) {
    return <PermissionDenied title="SCIM provisioning" message="Enterprise status read permission required." />
  }

  if (!flagOn) {
    return (
      <AdminPageShell title="SCIM provisioning & groups" subtitle="Diagnostics UI disabled in this environment.">
        <EmptyState
          title="SCIM diagnostics disabled"
          message="Enable SCIM_COMPATIBILITY_DIAGNOSTICS in non-production to load sync status."
        />
      </AdminPageShell>
    )
  }

  return (
    <AdminPageShell
      title="SCIM provisioning & groups"
      subtitle="Sync overview and compatibility warnings. No raw IdP claims or SCIM payloads are shown."
    >
      {statusQ.isLoading || runsQ.isLoading ? <LoadingState label="Loading SCIM status…" /> : null}
      {statusQ.isError ? <ErrorAlert error={statusQ.error} /> : null}
      {runsQ.isError ? <ErrorAlert error={runsQ.error} /> : null}

      {statusQ.data ? <ScimProvisioningCard status={statusQ.data} /> : null}

      {statusQ.data?.checkpoints?.length ? (
        <Card className="text-sm">
          <p className="font-semibold text-slate-900">Checkpoints</p>
          <ul className="mt-2 space-y-1 text-xs text-slate-700">
            {statusQ.data.checkpoints.map((cp) => (
              <li key={cp.id}>
                {cp.resourceType} · {cp.status} · checkpoint {cp.checkpointPresent ? 'present' : 'missing'}
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      {runsQ.data?.items?.length ? (
        <Card className="text-sm">
          <p className="font-semibold text-slate-900">Recent sync runs</p>
          <ul className="mt-2 space-y-1 text-xs">
            {runsQ.data.items.map((run) => (
              <li key={run.id}>
                {run.resourceType} {run.syncMode} · {run.status} · processed {run.processedCount} · errors{' '}
                {run.errorCount}
                {run.lastErrorSummary ? ` · ${run.lastErrorSummary}` : ''}
              </li>
            ))}
          </ul>
        </Card>
      ) : (
        <p className="text-sm text-slate-500">No sync runs in the current page.</p>
      )}

      <AdminRunbookLink docPath="docs/scim-provisioning.md" label="SCIM runbook" />
    </AdminPageShell>
  )
}
