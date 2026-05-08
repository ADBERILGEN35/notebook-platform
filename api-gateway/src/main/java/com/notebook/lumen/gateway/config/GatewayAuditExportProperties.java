package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.audit-export")
public record GatewayAuditExportProperties(
    boolean enabled,
    int maxRangeDays,
    int maxRecords,
    int pageSize,
    MachineAuth machineAuth,
    boolean scheduledExportConfigured,
    boolean archiveUploadEnabled,
    String archiveProvider) {
  public int effectiveMaxRangeDays() {
    return maxRangeDays <= 0 ? 31 : maxRangeDays;
  }

  public int effectiveMaxRecords() {
    return maxRecords <= 0 ? 10_000 : maxRecords;
  }

  public int effectivePageSize() {
    if (pageSize <= 0) {
      return 200;
    }
    return Math.min(pageSize, 200);
  }

  public MachineAuth effectiveMachineAuth() {
    return machineAuth == null
        ? new MachineAuth(false, "", "api-gateway", "admin:audit:export", "", "", 900)
        : machineAuth;
  }

  public String effectiveArchiveProvider() {
    return archiveProvider == null || archiveProvider.isBlank() ? "" : archiveProvider.trim();
  }

  public record MachineAuth(
      boolean enabled,
      String allowedIssuers,
      String audience,
      String requiredScope,
      String publicKeyPath,
      String publicKey,
      int maxTtlSeconds) {}
}
