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
    int providerMaxPageSize,
    boolean deltaProviderPocEnabled,
    boolean deltaDryRunOnly,
    boolean deltaRemoteFetchEnabled,
    int deltaHttpTimeoutMs,
    int deltaMaxRetryAfterSeconds,
    int deltaBackoffBaseSeconds,
    String deltaRemoteBaseUrl,
    String deltaRemoteTokenSecretName,
    String deltaRemoteTokenSecretKey,
    String deltaRemoteBearerToken,
    int deltaRemoteMaxPageSize,
    boolean deltaRemoteMultiPageEnabled,
    int deltaRemoteMaxPages,
    int deltaRemoteMaxResources,
    int deltaRemotePageDelayMs) {

  /** Test helper only — Spring binds via the canonical record constructor + application.yml. */
  public static ScimProperties withLegacyDefaults(
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
    return new ScimProperties(
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
        100,
        false,
        true,
        false,
        3000,
        300,
        30,
        "",
        "",
        "",
        "",
        100,
        false,
        1,
        500,
        0);
  }

  public boolean deltaRemoteBaseUrlConfigured() {
    return deltaRemoteBaseUrl != null && !deltaRemoteBaseUrl.isBlank();
  }

  public boolean deltaRemoteBearerTokenPresent() {
    return deltaRemoteBearerToken != null && !deltaRemoteBearerToken.isBlank();
  }

  public boolean deltaRemoteTokenSecretRefsPresent() {
    return deltaRemoteTokenSecretName != null
        && !deltaRemoteTokenSecretName.isBlank()
        && deltaRemoteTokenSecretKey != null
        && !deltaRemoteTokenSecretKey.isBlank();
  }

  /** True when flag is on and URL plus token material (bearer or secret refs) are set. */
  public boolean deltaRemoteFetchConfigured() {
    return deltaRemoteFetchEnabled()
        && deltaRemoteBaseUrlConfigured()
        && (deltaRemoteBearerTokenPresent() || deltaRemoteTokenSecretRefsPresent());
  }

  /** True when a live GET may be issued (bearer token required at runtime). */
  public boolean deltaRemoteFetchRuntimeReady() {
    return deltaRemoteFetchEnabled()
        && deltaRemoteBaseUrlConfigured()
        && deltaRemoteBearerTokenPresent();
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
