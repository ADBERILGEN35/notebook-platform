package com.notebook.lumen.identity.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentitySecurityStatusResponse(
    Sso sso,
    Scim scim,
    Mfa mfa,
    Siem siem,
    AdminRbac adminRbac,
    BreakGlass breakGlass,
    boolean unavailable,
    String unavailableReason) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AdminRbac(
      boolean enabled,
      boolean legacyPlatformAdminImpliesAll,
      Map<String, Boolean> rolesConfigured) {}

  public record Sso(
      boolean enabled,
      int providersConfigured,
      boolean allowedDomainsConfigured,
      boolean adminGroupMappingConfigured,
      boolean trustIdpMfa) {}

  public record Scim(
      boolean enabled,
      boolean groupsEnabled,
      boolean adminGroupsConfigured,
      boolean tokenConfigured) {}

  public record Mfa(boolean enabled, boolean webauthnEnabled) {}

  public record Siem(
      boolean enabled,
      String provider,
      boolean workerEnabled,
      boolean endpointConfigured,
      boolean secretConfigured) {}

  public record BreakGlass(
      boolean enabled,
      String credentialMode,
      java.util.List<String> allowedModes,
      boolean staticTokenConfigured,
      boolean webauthnEnabled,
      int webauthnCredentialCount,
      boolean offlineSignedEnabled,
      boolean offlinePublicKeyConfigured,
      boolean staticTokenRotationRecommended,
      java.time.Instant lastStaticTokenUsedAt,
      String approvalMode,
      long pendingReviewCount,
      long overdueReviewCount,
      int sessionTtlMinutes,
      int maxActiveSessions,
      boolean requireReason,
      boolean requireMfa) {}
}
