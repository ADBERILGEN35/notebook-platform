package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public record GatewayRateLimitProperties(
    Bucket auth,
    Bucket protectedApi,
    Bucket adminAudit,
    Bucket adminAuditExport,
    Bucket adminAuditExportMachine,
    Bucket scim) {
  public Bucket effectiveAdminAudit() {
    return adminAudit == null ? protectedApi : adminAudit;
  }

  public Bucket effectiveAdminAuditExport() {
    return adminAuditExport == null ? effectiveAdminAudit() : adminAuditExport;
  }

  public Bucket effectiveAdminAuditExportMachine() {
    return adminAuditExportMachine == null
        ? new Bucket(1, 1, 1)
        : adminAuditExportMachine;
  }

  public Bucket effectiveScim() {
    return scim == null ? auth : scim;
  }

  public record Bucket(int replenishRate, int burstCapacity, int requestedTokens) {}
}
