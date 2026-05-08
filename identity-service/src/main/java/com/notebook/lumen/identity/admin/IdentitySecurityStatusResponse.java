package com.notebook.lumen.identity.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentitySecurityStatusResponse(
    Sso sso, Scim scim, Mfa mfa, Siem siem, boolean unavailable, String unavailableReason) {

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
}
