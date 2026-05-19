import { Link } from 'react-router-dom'
import { useAuthStore } from '../../features/auth/auth-store'
import { PERM_ENTERPRISE_STATUS_READ, hasPlatformPermission } from '../../features/admin/access/admin-permissions'
import { useEnterpriseStatus } from '../../features/admin/enterprise/use-enterprise-status'
import { AdminPageShell } from '../../features/admin/shared/AdminPageShell'
import { IdentityStatusCard } from '../../features/admin/identity/IdentityStatusCard'
import { AdminRiskBadge } from '../../features/admin/shared/AdminRiskBadge'
import { AdminRunbookLink } from '../../features/admin/shared/AdminRunbookLink'
import { getAuditApiMode, isAdminUiDevOpen, isScimCompatibilityDiagnosticsUiEnabled } from '../../shared/config/admin-feature-flags'
import { LoadingState } from '../../shared/components/LoadingState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PermissionDenied } from '../../shared/components/PermissionDenied'
import { Card } from '../../shared/components/Card'

export function AdminIdentityOverviewPage() {
  const user = useAuthStore((s) => s.user)
  const devOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const canRead = devOpen || hasPlatformPermission(user, PERM_ENTERPRISE_STATUS_READ)
  const statusQ = useEnterpriseStatus()

  if (!canRead) {
    return <PermissionDenied title="Identity overview" message="Enterprise status read permission required." />
  }

  const f = statusQ.data?.features

  return (
    <AdminPageShell
      title="Identity & SSO overview"
      subtitle="OIDC/SAML, SCIM, and RBAC mapping health from aggregated gateway status (secret-safe)."
    >
      {statusQ.isLoading ? <LoadingState label="Loading identity snapshot…" /> : null}
      {statusQ.isError ? <ErrorAlert error={statusQ.error} /> : null}

      <div className="grid gap-3 sm:grid-cols-2">
        <IdentityStatusCard
          title="SSO"
          statusLabel={f?.sso?.enabled ? 'Enabled' : 'Off'}
          ok={!!f?.sso?.enabled && (f.sso.providersConfigured ?? 0) > 0}
          detail={`Providers ${f?.sso?.providersConfigured ?? 0}; admin mapping ${f?.sso?.adminGroupMappingConfigured ? 'yes' : 'no'}`}
        />
        <IdentityStatusCard
          title="SCIM"
          statusLabel={f?.scim?.enabled ? 'Enabled' : 'Off'}
          ok={!!f?.scim?.enabled && !!f.scim.tokenConfigured}
          detail={`Groups ${f?.scim?.groupsEnabled ? 'on' : 'off'}; nested ${f?.scim?.nestedGroupsSupported ? 'supported' : 'n/a'}`}
        />
        <IdentityStatusCard
          title="Admin MFA"
          statusLabel={f?.mfa?.identityMfaEnabled ? 'On' : 'Check'}
          ok={!!f?.mfa?.identityMfaEnabled}
          detail={f?.mfa ? `Mode ${f.mfa.adminMfaMode}` : undefined}
        />
        <IdentityStatusCard
          title="RBAC mapping"
          statusLabel={f?.sso?.adminGroupMappingConfigured ? 'Configured' : 'Gap'}
          ok={!!f?.sso?.adminGroupMappingConfigured}
          detail="External group → platform role mapping"
        />
      </div>

      {(statusQ.data?.warnings ?? []).length > 0 ? (
        <Card className="space-y-2">
          <p className="text-xs font-semibold uppercase text-slate-500">Configuration warnings</p>
          <ul className="space-y-2 text-sm">
            {statusQ.data!.warnings.map((w) => (
              <li key={w.code} className="flex flex-wrap items-center gap-2">
                <AdminRiskBadge severity={w.severity} />
                <span className="font-mono text-xs">{w.code}</span>
                <span className="text-slate-700">{w.message}</span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <div className="flex flex-wrap gap-3 text-sm">
        <Link className="text-primary-600 hover:underline" to="/app/admin/identity/sso">
          SSO diagnostics
        </Link>
        {isScimCompatibilityDiagnosticsUiEnabled() ? (
          <Link className="text-primary-600 hover:underline" to="/app/admin/identity/scim">
            SCIM provisioning
          </Link>
        ) : null}
        <Link className="text-primary-600 hover:underline" to="/app/admin/identity/role-mapping">
          Role mapping diagnostics
        </Link>
      </div>

      <AdminRunbookLink docPath="docs/enterprise-sso.md" label="SSO runbook" />
      <AdminRunbookLink docPath="docs/scim-provisioning.md" label="SCIM runbook" />
    </AdminPageShell>
  )
}
