import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_AUDIT_READ,
  PERM_CHANGE_REQUEST_LIST,
  PERM_ENTERPRISE_STATUS_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import { listChangeRequests } from '../../features/admin/enterprise/change-requests-api'
import { useEnterpriseStatus } from '../../features/admin/enterprise/use-enterprise-status'
import { AdminPageShell } from '../../features/admin/shared/AdminPageShell'
import { AdminHealthCard } from '../../features/admin/shared/AdminHealthCard'
import { AdminOverviewCard } from '../../features/admin/shared/AdminOverviewCard'
import { AdminRunbookLink } from '../../features/admin/shared/AdminRunbookLink'
import {
  getAuditApiMode,
  isAdminUiDevOpen,
  isBreakGlassReviewUiEnabled,
  isEnterpriseAdminWriteEnabled,
  isPlatformRetentionGovernanceUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { LoadingState } from '../../shared/components/LoadingState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PermissionDenied } from '../../shared/components/PermissionDenied'

export function AdminOverviewPage() {
  const user = useAuthStore((s) => s.user)
  const devOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const canEnterprise = devOpen || hasPlatformPermission(user, PERM_ENTERPRISE_STATUS_READ)
  const canAudit = devOpen || hasPlatformPermission(user, PERM_AUDIT_READ)
  const canChangeRequests =
    isEnterpriseAdminWriteEnabled() && (devOpen || hasPlatformPermission(user, PERM_CHANGE_REQUEST_LIST))

  const statusQ = useEnterpriseStatus()
  const pendingQ = useQuery({
    queryKey: ['admin-change-requests-pending'],
    queryFn: () => listChangeRequests('PENDING'),
    enabled: canChangeRequests,
  })

  if (!canEnterprise && !canAudit) {
    return (
      <PermissionDenied
        title="Admin overview restricted"
        message="Requires enterprise status or audit read permission."
      />
    )
  }

  const features = statusQ.data?.features
  const warnings = statusQ.data?.warnings ?? []
  const criticalCount = warnings.filter((w) => w.severity === 'CRITICAL').length

  return (
    <AdminPageShell
      title="Admin overview"
      subtitle="System health, security posture, and operational summaries (gateway snapshot; no secrets)."
    >
      {statusQ.isLoading ? <LoadingState label="Loading enterprise snapshot…" /> : null}
      {statusQ.isError ? <ErrorAlert error={statusQ.error} /> : null}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <AdminHealthCard
          title="Environment"
          statusLabel={statusQ.data?.environment ?? '—'}
          ok={!statusQ.isError}
          detail={statusQ.data ? `Generated ${statusQ.data.generatedAt}` : undefined}
        />
        <AdminHealthCard
          title="Security warnings"
          statusLabel={criticalCount > 0 ? `${criticalCount} critical` : 'Clear'}
          ok={criticalCount === 0}
          detail={`${warnings.length} active warning(s) in snapshot`}
        />
        <AdminHealthCard
          title="Pending change requests"
          statusLabel={canChangeRequests ? String(pendingQ.data?.items.length ?? '—') : 'N/A'}
          ok={!canChangeRequests || (pendingQ.data?.items.length ?? 0) === 0}
          detail={canChangeRequests ? 'GitOps / enterprise write path' : 'Change request UI disabled'}
        />
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        <AdminOverviewCard
          title="Audit"
          description={
            canAudit
              ? `Audit API mode: ${getAuditApiMode()}. Browse events with sanitized metadata.`
              : 'Audit read permission required.'
          }
          to={canAudit ? '/app/admin/audit' : undefined}
          linkLabel="Audit events"
        />
        <AdminOverviewCard
          title="Identity & SSO"
          description={
            features?.sso?.enabled
              ? `${features.sso.providersConfigured} SSO provider(s) configured.`
              : 'SSO status from enterprise snapshot.'
          }
          to={canEnterprise ? '/app/admin/identity' : undefined}
          linkLabel="Identity overview"
        />
        <AdminOverviewCard
          title="Retention"
          description={
            isPlatformRetentionGovernanceUiEnabled()
              ? 'Platform retention governance available.'
              : 'Retention UI flag off in this environment.'
          }
          to={isPlatformRetentionGovernanceUiEnabled() ? '/app/admin/retention/platform' : undefined}
          linkLabel="Platform retention"
        />
        <AdminOverviewCard
          title="Break-glass"
          description={
            isBreakGlassReviewUiEnabled()
              ? `Enabled: ${features?.breakGlass?.enabled ? 'yes' : 'no'} · overdue reviews ${features?.breakGlass?.overdueReviewCount ?? 0}`
              : 'Break-glass review UI disabled (feature flag).'
          }
          to={isBreakGlassReviewUiEnabled() ? '/app/admin/security/break-glass' : undefined}
          linkLabel="Break-glass ops"
        />
      </div>

      <AdminRunbookLink docPath="docs/enterprise-admin-console.md" label="Enterprise console runbook" />
      <AdminRunbookLink docPath="docs/admin-audit-proxy.md" label="Audit runbook" />
      <p className="text-xs text-slate-500">
        Legacy enterprise console:{' '}
        <Link className="text-primary-600 hover:underline" to="/app/admin/enterprise">
          /app/admin/enterprise
        </Link>
      </p>
    </AdminPageShell>
  )
}
