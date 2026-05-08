package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.audit-export")
public record GatewayAuditExportProperties(
    boolean enabled, int maxRangeDays, int maxRecords, int pageSize) {
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
}
