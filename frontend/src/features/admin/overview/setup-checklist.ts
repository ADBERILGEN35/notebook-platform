import type { EnterpriseStatusResponse } from '../enterprise/enterprise-schema'

export type SetupChecklistStatus = 'completed' | 'warning' | 'blocked' | 'disabled'

export type SetupChecklistItem = {
  id: string
  label: string
  status: SetupChecklistStatus
  detail: string
  runbookPath?: string
}

export function buildSetupChecklistItems(
  status: EnterpriseStatusResponse | undefined,
  flags: {
    auditUi: boolean
    scimDiagnostics: boolean
    platformRetention: boolean
    breakGlassReview: boolean
  },
): SetupChecklistItem[] {
  if (!status) {
    return [
      {
        id: 'enterprise-status',
        label: 'Enterprise status snapshot',
        status: 'blocked',
        detail: 'Unable to load configuration snapshot from gateway.',
        runbookPath: 'docs/enterprise-admin-console.md',
      },
    ]
  }

  const sso = status.features.sso
  const scim = status.features.scim
  const mfa = status.features.mfa
  const audit = status.features.auditExport
  const bg = status.features.breakGlass

  const items: SetupChecklistItem[] = [
    {
      id: 'sso',
      label: 'SSO (OIDC/SAML)',
      status: !sso?.enabled
        ? 'disabled'
        : sso.providersConfigured > 0 && sso.adminGroupMappingConfigured
          ? 'completed'
          : 'warning',
      detail: sso?.enabled
        ? `${sso.providersConfigured} provider(s); admin group mapping ${sso.adminGroupMappingConfigured ? 'configured' : 'missing'}`
        : 'SSO not enabled',
      runbookPath: 'docs/enterprise-sso.md',
    },
    {
      id: 'scim',
      label: 'SCIM provisioning',
      status: !scim?.enabled
        ? 'disabled'
        : scim.tokenConfigured && scim.adminGroupsConfigured
          ? 'completed'
          : 'warning',
      detail: scim?.enabled
        ? `Provider ${scim.providerType ?? 'unknown'}; token ${scim.tokenConfigured ? 'configured' : 'missing'}`
        : 'SCIM not enabled',
      runbookPath: 'docs/scim-provisioning.md',
    },
    {
      id: 'mfa',
      label: 'Admin MFA',
      status: mfa?.identityMfaEnabled ? 'completed' : 'warning',
      detail: mfa
        ? `Mode ${mfa.adminMfaMode}; methods: ${mfa.acceptedMethods.join(', ') || 'none'}`
        : 'MFA posture unavailable',
      runbookPath: 'docs/break-glass-admin-access.md',
    },
    {
      id: 'audit-export',
      label: 'Audit export',
      status: !flags.auditUi
        ? 'disabled'
        : audit?.enabled
          ? 'completed'
          : 'warning',
      detail: audit?.enabled
        ? `Archive ${audit.archiveProvider}; scheduled ${audit.scheduledExportConfigured ? 'yes' : 'no'}`
        : 'Audit export not enabled',
      runbookPath: 'docs/admin-audit-proxy.md',
    },
    {
      id: 'retention',
      label: 'Platform retention governance',
      status: !flags.platformRetention ? 'disabled' : 'warning',
      detail: flags.platformRetention
        ? 'Retention governance UI enabled; verify targets and legal holds in admin console.'
        : 'Platform retention UI flag off',
      runbookPath: 'docs/platform-retention-governance.md',
    },
    {
      id: 'break-glass',
      label: 'Break-glass operations',
      status: !flags.breakGlassReview
        ? 'disabled'
        : bg?.enabled
          ? bg.gatewayAllowed && bg.adminWriteAllowed
            ? 'completed'
            : 'warning'
          : 'blocked',
      detail: bg?.enabled
        ? `Gateway ${bg.gatewayAllowed ? 'allowed' : 'blocked'}; admin write ${bg.adminWriteAllowed ? 'allowed' : 'blocked'}`
        : 'Break-glass not enabled',
      runbookPath: 'docs/break-glass-admin-access.md',
    },
  ]

  if (flags.scimDiagnostics && scim?.enabled) {
    items.push({
      id: 'scim-diagnostics',
      label: 'SCIM compatibility diagnostics',
      status: 'completed',
      detail: 'Read-only SCIM sync diagnostics available under Identity → SCIM.',
      runbookPath: 'docs/scim-provisioning.md',
    })
  }

  if (status.identityUnavailable) {
    items.push({
      id: 'identity-unavailable',
      label: 'Identity service reachability',
      status: 'blocked',
      detail: 'Identity status partial or unavailable in this snapshot.',
    })
  }

  return items
}
