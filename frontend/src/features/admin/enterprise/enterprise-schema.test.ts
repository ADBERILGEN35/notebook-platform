import { describe, expect, it } from 'vitest'
import { enterpriseStatusResponseSchema } from './enterprise-schema'

describe('enterpriseStatusResponseSchema', () => {
  it('parses gateway-shaped payload', () => {
    const parsed = enterpriseStatusResponseSchema.safeParse({
      environment: 'test',
      generatedAt: '2026-01-01T00:00:00Z',
      features: {
        sso: { enabled: true, providersConfigured: 1, allowedDomainsConfigured: true, adminGroupMappingConfigured: true, trustIdpMfa: false },
        scim: {
          enabled: false,
          groupsEnabled: false,
          adminGroupsConfigured: false,
          tokenConfigured: false,
          providerType: 'generic',
          deltaSyncEnabled: false,
          deltaSyncMode: 'disabled',
          bulkSupported: false,
          filteringSupported: true,
          patchSupported: true,
          nestedGroupsSupported: false,
          rateLimitAware: true,
          maxPageSize: 100,
        },
        mfa: { adminMfaMode: 'warn', acceptedMethods: ['webauthn'], identityMfaEnabled: true, webauthnEnabled: true },
        siem: { enabled: false, provider: 'noop', workerEnabled: false, endpointConfigured: false, secretConfigured: false },
        auditExport: {
          enabled: true,
          machineAuthEnabled: true,
          scheduledExportConfigured: false,
          archiveUploadEnabled: false,
          archiveProvider: '',
          machineAuthPublicKeyConfigured: true,
        },
        notifications: {
          sseEnabled: true,
          distributedFanoutEnabled: false,
          digestEnabled: true,
          digestWorkerEnabled: true,
        },
        gatewaySecurity: {
          adminEnabled: true,
          adminAuditEnabled: true,
          adminMfaMode: 'warn',
          adminMfaAcceptedMethods: ['webauthn'],
          rateLimitEnabled: true,
          csrfEnabled: false,
          authTransport: 'bearer',
          cookieModeEnabled: false,
        },
      },
      warnings: [{ code: 'ADMIN_MFA_MODE_NOT_ENFORCE', message: 'x', severity: 'INFO' }],
      identityUnavailable: false,
      notificationUnavailable: false,
    })
    expect(parsed.success).toBe(true)
  })
})
