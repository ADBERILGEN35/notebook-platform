import { z } from 'zod'

export const warningSeveritySchema = z.enum(['INFO', 'WARNING', 'CRITICAL'])

export const enterpriseWarningSchema = z.object({
  code: z.string(),
  message: z.string(),
  severity: warningSeveritySchema,
})

export const enterpriseStatusResponseSchema = z.object({
  environment: z.string(),
  generatedAt: z.string(),
  features: z.object({
    sso: z
      .object({
        enabled: z.boolean(),
        providersConfigured: z.number(),
        allowedDomainsConfigured: z.boolean(),
        adminGroupMappingConfigured: z.boolean(),
        trustIdpMfa: z.boolean(),
      })
      .optional(),
    scim: z
      .object({
        enabled: z.boolean(),
        groupsEnabled: z.boolean(),
        adminGroupsConfigured: z.boolean(),
        tokenConfigured: z.boolean(),
      })
      .optional(),
    mfa: z
      .object({
        adminMfaMode: z.string(),
        acceptedMethods: z.array(z.string()),
        identityMfaEnabled: z.boolean(),
        webauthnEnabled: z.boolean(),
      })
      .optional(),
    siem: z
      .object({
        enabled: z.boolean(),
        provider: z.string(),
        workerEnabled: z.boolean(),
        endpointConfigured: z.boolean(),
        secretConfigured: z.boolean(),
      })
      .optional(),
    breakGlass: z
      .object({
        enabled: z.boolean(),
        tokenConfigured: z.boolean(),
        gatewayAllowed: z.boolean(),
        adminWriteAllowed: z.boolean(),
        sessionTtlMinutes: z.number(),
        maxActiveSessions: z.number(),
        requireReason: z.boolean(),
        requireMfa: z.boolean(),
      })
      .optional(),
    auditExport: z
      .object({
        enabled: z.boolean(),
        machineAuthEnabled: z.boolean(),
        scheduledExportConfigured: z.boolean(),
        archiveUploadEnabled: z.boolean(),
        archiveProvider: z.string(),
        machineAuthPublicKeyConfigured: z.boolean(),
      })
      .optional(),
    notifications: z
      .object({
        sseEnabled: z.boolean(),
        distributedFanoutEnabled: z.boolean(),
        digestEnabled: z.boolean(),
        digestWorkerEnabled: z.boolean(),
      })
      .optional(),
    gatewaySecurity: z
      .object({
        adminEnabled: z.boolean(),
        adminAuditEnabled: z.boolean(),
        adminMfaMode: z.string(),
        adminMfaAcceptedMethods: z.array(z.string()),
        rateLimitEnabled: z.boolean(),
        csrfEnabled: z.boolean(),
        authTransport: z.string(),
        cookieModeEnabled: z.boolean(),
      })
      .optional(),
    mergeResolution: z
      .object({
        analysisEnabled: z.boolean(),
        applyEnabled: z.boolean(),
        supportedVersions: z.array(z.number()),
        idempotencyEnabled: z.boolean(),
        metricsEnabled: z.boolean(),
        auditFailuresEnabled: z.boolean(),
      })
      .optional(),
  }),
  warnings: z.array(enterpriseWarningSchema),
  identityUnavailable: z.boolean().optional(),
  notificationUnavailable: z.boolean().optional(),
  contentUnavailable: z.boolean().optional(),
})

export type EnterpriseStatusResponse = z.infer<typeof enterpriseStatusResponseSchema>
export type EnterpriseWarning = z.infer<typeof enterpriseWarningSchema>
export type WarningSeverity = z.infer<typeof warningSeveritySchema>
