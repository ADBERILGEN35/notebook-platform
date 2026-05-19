import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_AUDIT_READ,
  PERM_ENTERPRISE_STATUS_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import { useEnterpriseStatus } from '../../features/admin/enterprise/use-enterprise-status'
import { AdminPageShell } from '../../features/admin/shared/AdminPageShell'
import { AdminSetupChecklist } from '../../features/admin/overview/AdminSetupChecklist'
import { buildSetupChecklistItems } from '../../features/admin/overview/setup-checklist'
import {
  getAuditApiMode,
  isAdminUiDevOpen,
  isBreakGlassReviewUiEnabled,
  isPlatformRetentionGovernanceUiEnabled,
  isScimCompatibilityDiagnosticsUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { LoadingState } from '../../shared/components/LoadingState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PermissionDenied } from '../../shared/components/PermissionDenied'

export function AdminSetupChecklistPage() {
  const user = useAuthStore((s) => s.user)
  const devOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const canRead = devOpen || hasPlatformPermission(user, PERM_ENTERPRISE_STATUS_READ)
  const statusQ = useEnterpriseStatus()

  if (!canRead) {
    return <PermissionDenied title="Setup checklist" message="Enterprise status read permission required." />
  }

  const items = buildSetupChecklistItems(statusQ.data, {
    auditUi: devOpen || hasPlatformPermission(user, PERM_AUDIT_READ),
    scimDiagnostics: isScimCompatibilityDiagnosticsUiEnabled(),
    platformRetention: isPlatformRetentionGovernanceUiEnabled(),
    breakGlassReview: isBreakGlassReviewUiEnabled(),
  })

  return (
    <AdminPageShell
      title="Admin setup checklist"
      subtitle="Enterprise readiness steps derived from gateway configuration snapshot (read-only)."
    >
      {statusQ.isLoading ? <LoadingState label="Loading checklist…" /> : null}
      {statusQ.isError ? <ErrorAlert error={statusQ.error} /> : null}
      <AdminSetupChecklist items={items} />
    </AdminPageShell>
  )
}
