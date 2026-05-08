package com.notebook.lumen.gateway.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin")
public record GatewayAdminProperties(
    boolean enabled, String allowedUserIds, String allowedEmails, Audit audit) {

  public Set<String> allowedUserIdSet() {
    return splitCsv(allowedUserIds);
  }

  public Set<String> allowedEmailSet() {
    return splitCsv(allowedEmails).stream()
        .map(value -> value.toLowerCase(java.util.Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  public Audit effectiveAudit() {
    return audit == null ? new Audit(false) : audit;
  }

  private static Set<String> splitCsv(String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  public record Audit(boolean enabled) {}
}
