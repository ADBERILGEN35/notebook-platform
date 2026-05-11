package com.notebook.lumen.gateway.admin.enterprise;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EnterpriseStatusWarningEngine {

  public List<EnterpriseWarning> build(
      EnterpriseStatusFeatures features,
      boolean identityUnavailable,
      boolean notificationUnavailable,
      boolean contentUnavailable) {
    List<EnterpriseWarning> warnings = new ArrayList<>();
    if (identityUnavailable) {
      warnings.add(
          new EnterpriseWarning(
              "IDENTITY_STATUS_UNAVAILABLE",
              "Could not load identity-service enterprise security status (partial data).",
              WarningSeverity.WARNING));
    }
    if (notificationUnavailable) {
      warnings.add(
          new EnterpriseWarning(
              "NOTIFICATION_STATUS_UNAVAILABLE",
              "Could not load notification-service status (partial data).",
              WarningSeverity.WARNING));
    }
    if (contentUnavailable) {
      warnings.add(
          new EnterpriseWarning(
              "CONTENT_STATUS_UNAVAILABLE",
              "Could not load content-service merge status (partial data).",
              WarningSeverity.WARNING));
    }
    if (features == null) {
      return warnings;
    }
    if (features.sso() != null && features.sso().enabled()) {
      if (!features.sso().adminGroupMappingConfigured()) {
        warnings.add(
            new EnterpriseWarning(
                "SSO_ADMIN_MAPPING_MISSING",
                "SSO is enabled but admin group mapping is not configured.",
                WarningSeverity.WARNING));
      }
      if (features.sso().providersConfigured() <= 0) {
        warnings.add(
            new EnterpriseWarning(
                "SSO_NO_PROVIDERS",
                "SSO is enabled but no fully configured OIDC providers were found.",
                WarningSeverity.CRITICAL));
      }
    }
    if (features.scim() != null
        && features.scim().enabled()
        && !features.scim().tokenConfigured()) {
      warnings.add(
          new EnterpriseWarning(
              "SCIM_TOKEN_MISSING",
              "SCIM is enabled but bearer token is not configured.",
              WarningSeverity.CRITICAL));
    }
    if (features.mfa() != null) {
      String mode = features.mfa().adminMfaMode() == null ? "off" : features.mfa().adminMfaMode();
      if (!"enforce".equals(mode)) {
        warnings.add(
            new EnterpriseWarning(
                "ADMIN_MFA_MODE_NOT_ENFORCE",
                "Admin MFA is not in enforce mode.",
                mode.equals("off") ? WarningSeverity.WARNING : WarningSeverity.INFO));
      }
    }
    if (features.siem() != null && features.siem().enabled()) {
      if (!features.siem().workerEnabled()) {
        warnings.add(
            new EnterpriseWarning(
                "SIEM_WORKER_DISABLED",
                "SIEM push is enabled but the outbox worker is disabled.",
                WarningSeverity.WARNING));
      }
      if (!features.siem().secretConfigured()) {
        warnings.add(
            new EnterpriseWarning(
                "SIEM_SECRET_NOT_CONFIGURED",
                "SIEM push is enabled but outbound auth secret is not configured for the selected auth mode.",
                WarningSeverity.CRITICAL));
      }
    }
    if (features.auditExport() != null && features.auditExport().enabled()) {
      if (!features.auditExport().machineAuthEnabled()) {
        warnings.add(
            new EnterpriseWarning(
                "AUDIT_EXPORT_MACHINE_AUTH_DISABLED",
                "Audit export is enabled but machine authentication for scheduled export is disabled.",
                WarningSeverity.INFO));
      }
      if (!features.auditExport().scheduledExportConfigured()) {
        warnings.add(
            new EnterpriseWarning(
                "AUDIT_SCHEDULED_EXPORT_NOT_FLAGGED",
                "Scheduled audit export is not flagged as configured on the gateway (operator-managed).",
                WarningSeverity.INFO));
      }
      if (!features.auditExport().archiveUploadEnabled()) {
        warnings.add(
            new EnterpriseWarning(
                "AUDIT_ARCHIVE_UPLOAD_DISABLED",
                "Archive upload is not flagged as enabled (operator-managed object storage).",
                WarningSeverity.INFO));
      }
    }
    if (features.notifications() != null) {
      if (features.notifications().sseEnabled()
          && !features.notifications().distributedFanoutEnabled()) {
        warnings.add(
            new EnterpriseWarning(
                "NOTIFICATION_SSE_WITHOUT_DISTRIBUTED_FANOUT",
                "SSE is enabled but distributed fan-out is disabled (single-instance realtime).",
                WarningSeverity.INFO));
      }
      if (features.notifications().digestEnabled()
          && !features.notifications().digestWorkerEnabled()) {
        warnings.add(
            new EnterpriseWarning(
                "NOTIFICATION_DIGEST_WORKER_DISABLED",
                "Digest emails are enabled but the digest worker is disabled.",
                WarningSeverity.WARNING));
      }
    }
    if (features.mergeResolution() != null) {
      if (!features.mergeResolution().applyEnabled()) {
        warnings.add(
            new EnterpriseWarning(
                "MERGE_APPLY_DISABLED",
                "Backend semantic merge apply is disabled.",
                WarningSeverity.INFO));
      }
      if (!features.mergeResolution().auditFailuresEnabled()) {
        warnings.add(
            new EnterpriseWarning(
                "MERGE_AUDIT_FAILURES_DISABLED",
                "Merge apply failure audit events are disabled.",
                WarningSeverity.INFO));
      }
    }
    return warnings;
  }
}
