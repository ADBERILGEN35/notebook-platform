package com.notebook.lumen.identity.scim;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.scim")
public record ScimProperties(
    boolean enabled,
    String bearerToken,
    String bearerTokenHash,
    boolean groupsEnabled,
    String adminGroups,
    boolean groupNestingEnabled,
    int groupNestingMaxDepth,
    boolean bulkEnabled,
    int bulkMaxOperations,
    int bulkFailOnErrorsMax,
    String providerType,
    boolean deltaSyncEnabled,
    String deltaSyncMode,
    boolean providerSupportsBulk,
    boolean providerSupportsFiltering,
    boolean providerSupportsPatch,
    boolean providerSupportsNestedGroups,
    boolean providerRateLimitAware,
    int providerMaxPageSize) {

  public ScimProperties(
      boolean enabled,
      String bearerToken,
      String bearerTokenHash,
      boolean groupsEnabled,
      String adminGroups,
      boolean groupNestingEnabled,
      int groupNestingMaxDepth,
      boolean bulkEnabled,
      int bulkMaxOperations,
      int bulkFailOnErrorsMax) {
    this(
        enabled,
        bearerToken,
        bearerTokenHash,
        groupsEnabled,
        adminGroups,
        groupNestingEnabled,
        groupNestingMaxDepth,
        bulkEnabled,
        bulkMaxOperations,
        bulkFailOnErrorsMax,
        "generic",
        false,
        "disabled",
        false,
        true,
        true,
        false,
        true,
        100);
  }

  public boolean authConfigured() {
    return (bearerToken != null && !bearerToken.isBlank())
        || (bearerTokenHash != null && !bearerTokenHash.isBlank());
  }

  public Set<String> adminGroupSet() {
    if (adminGroups == null || adminGroups.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(adminGroups.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .map(String::toLowerCase)
        .collect(Collectors.toUnmodifiableSet());
  }
}
