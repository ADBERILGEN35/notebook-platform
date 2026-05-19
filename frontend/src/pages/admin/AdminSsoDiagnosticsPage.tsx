import { useAuthStore } from '../../features/auth/auth-store'
import { PERM_ENTERPRISE_STATUS_READ, hasPlatformPermission } from '../../features/admin/access/admin-permissions'
import { useEnterpriseStatus } from '../../features/admin/enterprise/use-enterprise-status'
import { AdminPageShell } from '../../features/admin/shared/AdminPageShell'
import { SsoDiagnosticCard } from '../../features/admin/identity/SsoDiagnosticCard'
import { AdminRunbookLink } from '../../features/admin/shared/AdminRunbookLink'
import { getAuditApiMode, isAdminUiDevOpen } from '../../shared/config/admin-feature-flags'
import { LoadingState } from '../../shared/components/LoadingState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PermissionDenied } from '../../shared/components/PermissionDenied'

export function AdminSsoDiagnosticsPage() {
  const user = useAuthStore((s) => s.user)
  const devOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const canRead = devOpen || hasPlatformPermission(user, PERM_ENTERPRISE_STATUS_READ)
  const statusQ = useEnterpriseStatus()
  const sso = statusQ.data?.features.sso

  if (!canRead) {
    return <PermissionDenied title="SSO diagnostics" message="Enterprise status read permission required." />
  }

  return (
    <AdminPageShell
      title="SSO diagnostics"
      subtitle="Secret-safe checks from configuration snapshot. JWKS reachability and redirect URI validation are performed server-side."
    >
      {statusQ.isLoading ? <LoadingState label="Loading SSO diagnostics…" /> : null}
      {statusQ.isError ? <ErrorAlert error={statusQ.error} /> : null}

      <SsoDiagnosticCard
        title="Provider posture"
        rows={[
          { label: 'SSO enabled', value: sso?.enabled ? 'yes' : 'no', ok: !!sso?.enabled },
          { label: 'Providers configured', value: String(sso?.providersConfigured ?? 0), ok: (sso?.providersConfigured ?? 0) > 0 },
          { label: 'Allowed domains', value: sso?.allowedDomainsConfigured ? 'configured' : 'missing', ok: !!sso?.allowedDomainsConfigured },
          { label: 'Admin group mapping', value: sso?.adminGroupMappingConfigured ? 'configured' : 'missing', ok: !!sso?.adminGroupMappingConfigured },
          { label: 'Trust IdP MFA', value: sso?.trustIdpMfa ? 'yes' : 'no' },
        ]}
      />

      <SsoDiagnosticCard
        title="Operational checks (server-side)"
        rows={[
          { label: 'JWKS reachability', value: 'Validated by identity service (not exposed to browser)' },
          { label: 'Redirect URI alignment', value: 'Checked during provider configuration' },
          { label: 'Clock skew tolerance', value: 'Enforced at token validation' },
          { label: 'Provider errors', value: 'See audit events and gateway logs (sanitized)' },
        ]}
      />

      <AdminRunbookLink docPath="docs/enterprise-sso.md" label="SSO runbook" />
    </AdminPageShell>
  )
}
