import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_RBAC_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import * as rbacApi from '../../features/admin/rbac/admin-rbac-api'
import { useEnterpriseStatus } from '../../features/admin/enterprise/use-enterprise-status'
import { AdminPageShell } from '../../features/admin/shared/AdminPageShell'
import { RoleMappingTable } from '../../features/admin/identity/RoleMappingTable'
import { RoleMappingWarningCard } from '../../features/admin/identity/RoleMappingWarningCard'
import { AdminRunbookLink } from '../../features/admin/shared/AdminRunbookLink'
import {
  getAuditApiMode,
  isAdminRbacOverridesStatusUiEnabled,
  isAdminRbacUiEnabled,
  isAdminUiDevOpen,
} from '../../shared/config/admin-feature-flags'
import { LoadingState } from '../../shared/components/LoadingState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PermissionDenied } from '../../shared/components/PermissionDenied'
import { Card } from '../../shared/components/Card'
import { Input } from '../../shared/components/Input'

export function AdminRoleMappingDiagnosticsPage() {
  const user = useAuthStore((s) => s.user)
  const devOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const canRead = devOpen || hasPlatformPermission(user, PERM_RBAC_READ)
  const uiOn = isAdminRbacUiEnabled()
  const [q, setQ] = useState('')

  const statusQ = useEnterpriseStatus()
  const overridesQ = useQuery({
    queryKey: ['admin-rbac-overrides-status'],
    queryFn: () => rbacApi.getAdminRbacOverridesStatus(),
    enabled: uiOn && canRead && isAdminRbacOverridesStatusUiEnabled(),
  })
  const usersQ = useQuery({
    queryKey: ['admin-rbac-users-mapping', q],
    queryFn: () => rbacApi.listAdminRbacUsers({ q: q || undefined, page: 0, size: 15 }),
    enabled: uiOn && canRead,
  })

  const broadAdmin = (usersQ.data?.items ?? []).filter((u) =>
    u.platformRoles.some((r) => r === 'PLATFORM_ADMIN' || r === 'ADMIN'),
  )

  if (!uiOn) {
    return (
      <AdminPageShell title="Role mapping diagnostics" subtitle="Admin RBAC UI is disabled (feature flag).">
        <p className="text-sm text-slate-600">Enable ADMIN_RBAC_UI_ENABLED in non-production to load mapping diagnostics.</p>
      </AdminPageShell>
    )
  }

  if (!canRead) {
    return <PermissionDenied title="Role mapping" message="admin:rbac:read permission required." />
  }

  return (
    <AdminPageShell
      title="Admin role mapping diagnostics"
      subtitle="External group → platform role mapping. Least-privilege guidance; no raw IdP claims."
    >
      {!statusQ.data?.features.sso?.adminGroupMappingConfigured ? (
        <RoleMappingWarningCard
          title="Admin group mapping gap"
          message="SSO is enabled but admin group mapping is not fully configured. Review enterprise warnings and SSO runbook."
        />
      ) : null}

      {broadAdmin.length > 0 ? (
        <RoleMappingWarningCard
          title="Broad admin role detected"
          message={`${broadAdmin.length} user(s) have PLATFORM_ADMIN or ADMIN via mapped sources. Prefer scoped platform roles.`}
        />
      ) : null}

      {overridesQ.data ? (
        <Card className="text-sm space-y-1">
          <p className="font-semibold text-slate-900">GitOps overrides</p>
          <p>
            Loaded: {overridesQ.data.loaded ? 'yes' : 'no'} · assignments {overridesQ.data.validAssignmentCount} ·
            warnings {overridesQ.data.warningCount}
          </p>
          <p className="font-mono text-xs">Checksum {rbacApi.formatOverrideChecksumShort(overridesQ.data.checksum)}</p>
        </Card>
      ) : null}

      <label className="block text-sm font-medium text-slate-700">
        Filter users
        <Input
          className="mt-1"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="email or user id"
        />
      </label>

      {usersQ.isLoading ? <LoadingState label="Loading mappings…" /> : null}
      {usersQ.isError ? <ErrorAlert error={usersQ.error} /> : null}
      {usersQ.data ? <RoleMappingTable rows={usersQ.data.items} /> : null}

      <AdminRunbookLink docPath="docs/enterprise-sso.md" label="SSO / group mapping runbook" />
    </AdminPageShell>
  )
}
