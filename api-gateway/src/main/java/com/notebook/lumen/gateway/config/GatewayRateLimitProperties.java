package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public record GatewayRateLimitProperties(
    Bucket auth, Bucket protectedApi, Bucket adminAudit, Bucket adminAuditExport) {
  public Bucket effectiveAdminAudit() {
    return adminAudit == null ? protectedApi : adminAudit;
  }

  public Bucket effectiveAdminAuditExport() {
    return adminAuditExport == null ? effectiveAdminAudit() : adminAuditExport;
  }

  public record Bucket(int replenishRate, int burstCapacity, int requestedTokens) {}
}
