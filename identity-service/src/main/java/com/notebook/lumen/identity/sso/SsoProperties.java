package com.notebook.lumen.identity.sso;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.sso")
public record SsoProperties(
    boolean enabled,
    int stateTtlSeconds,
    boolean trustIdpMfa,
    String requiredAcr,
    String requiredAmr,
    List<Provider> providers) {

  public int effectiveStateTtlSeconds() {
    return stateTtlSeconds <= 0 ? 300 : stateTtlSeconds;
  }

  public String effectiveRequiredAcr() {
    return requiredAcr == null ? "" : requiredAcr.trim();
  }

  public Set<String> requiredAmrSet() {
    return csvSet(requiredAmr);
  }

  public List<Provider> enabledProviders() {
    if (providers == null) {
      return List.of();
    }
    return providers.stream()
        .filter(
            provider ->
                provider != null
                    && !provider.registrationId().isBlank()
                    && !provider.issuerUri().isBlank()
                    && !provider.clientId().isBlank()
                    && !provider.clientSecret().isBlank())
        .toList();
  }

  private static Set<String> csvSet(String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> value.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  public record Provider(
      String registrationId,
      String issuerUri,
      String clientId,
      String clientSecret,
      String scopes,
      String emailClaim,
      String groupsClaim,
      String adminGroups,
      String allowedDomains) {

    public Provider {
      registrationId = registrationId == null ? "" : registrationId.trim();
      issuerUri = issuerUri == null ? "" : issuerUri.trim();
      clientId = clientId == null ? "" : clientId.trim();
      clientSecret = clientSecret == null ? "" : clientSecret.trim();
      scopes = scopes == null || scopes.isBlank() ? "openid,email,profile" : scopes;
      emailClaim = emailClaim == null || emailClaim.isBlank() ? "email" : emailClaim;
      groupsClaim = groupsClaim == null || groupsClaim.isBlank() ? "groups" : groupsClaim;
      adminGroups = adminGroups == null ? "" : adminGroups;
      allowedDomains = allowedDomains == null ? "" : allowedDomains;
    }

    public Set<String> scopeSet() {
      return csvSet(scopes);
    }

    public Set<String> adminGroupSet() {
      return csvSet(adminGroups);
    }

    public Set<String> allowedDomainSet() {
      return csvSet(allowedDomains);
    }
  }
}
