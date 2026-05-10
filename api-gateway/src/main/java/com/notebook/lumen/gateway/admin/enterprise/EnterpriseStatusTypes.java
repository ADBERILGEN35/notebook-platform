package com.notebook.lumen.gateway.admin.enterprise;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EnterpriseStatusResponse(
    String environment,
    Instant generatedAt,
    EnterpriseStatusFeatures features,
    List<EnterpriseWarning> warnings,
    boolean identityUnavailable,
    boolean notificationUnavailable,
    boolean contentUnavailable) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record EnterpriseStatusFeatures(
    SsoStatus sso,
    ScimStatus scim,
    MfaStatus mfa,
    SiemStatus siem,
    AuditExportStatus auditExport,
    NotificationsStatus notifications,
    GatewaySecurityStatus gatewaySecurity,
    MergeResolutionStatus mergeResolution) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record MergeResolutionStatus(
    boolean analysisEnabled,
    boolean applyEnabled,
    List<Integer> supportedVersions,
    boolean idempotencyEnabled,
    boolean metricsEnabled,
    boolean auditFailuresEnabled) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record SsoStatus(
    boolean enabled,
    int providersConfigured,
    boolean allowedDomainsConfigured,
    boolean adminGroupMappingConfigured,
    boolean trustIdpMfa) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record ScimStatus(
    boolean enabled,
    boolean groupsEnabled,
    boolean adminGroupsConfigured,
    boolean tokenConfigured) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record MfaStatus(
    String adminMfaMode,
    List<String> acceptedMethods,
    boolean identityMfaEnabled,
    boolean webauthnEnabled) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record SiemStatus(
    boolean enabled,
    String provider,
    boolean workerEnabled,
    boolean endpointConfigured,
    boolean secretConfigured) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record AuditExportStatus(
    boolean enabled,
    boolean machineAuthEnabled,
    boolean scheduledExportConfigured,
    boolean archiveUploadEnabled,
    String archiveProvider,
    boolean machineAuthPublicKeyConfigured) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record NotificationsStatus(
    boolean sseEnabled,
    boolean distributedFanoutEnabled,
    boolean digestEnabled,
    boolean digestWorkerEnabled) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record GatewaySecurityStatus(
    boolean adminEnabled,
    boolean adminAuditEnabled,
    String adminMfaMode,
    List<String> adminMfaAcceptedMethods,
    boolean rateLimitEnabled,
    boolean csrfEnabled,
    String authTransport,
    boolean cookieModeEnabled) {}

record EnterpriseWarning(String code, String message, WarningSeverity severity) {}

enum WarningSeverity {
  INFO,
  WARNING,
  CRITICAL
}
