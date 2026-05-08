package com.notebook.lumen.identity.admin;

import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.siem.SiemProperties;
import com.notebook.lumen.identity.sso.SsoProperties;
import org.springframework.stereotype.Service;

@Service
public class IdentitySecurityStatusService {
  private final SsoProperties ssoProperties;
  private final ScimProperties scimProperties;
  private final MfaProperties mfaProperties;
  private final SiemProperties siemProperties;

  public IdentitySecurityStatusService(
      SsoProperties ssoProperties,
      ScimProperties scimProperties,
      MfaProperties mfaProperties,
      SiemProperties siemProperties) {
    this.ssoProperties = ssoProperties;
    this.scimProperties = scimProperties;
    this.mfaProperties = mfaProperties;
    this.siemProperties = siemProperties;
  }

  public IdentitySecurityStatusResponse build() {
    var sso =
        new IdentitySecurityStatusResponse.Sso(
            ssoProperties.enabled(),
            ssoProperties.enabledProviders().size(),
            ssoProperties.enabledProviders().stream()
                .anyMatch(p -> !p.allowedDomainSet().isEmpty()),
            ssoProperties.enabledProviders().stream().anyMatch(p -> !p.adminGroupSet().isEmpty()),
            ssoProperties.trustIdpMfa());
    var scim =
        new IdentitySecurityStatusResponse.Scim(
            scimProperties.enabled(),
            scimProperties.groupsEnabled(),
            !scimProperties.adminGroupSet().isEmpty(),
            scimProperties.authConfigured());
    var mfa =
        new IdentitySecurityStatusResponse.Mfa(
            mfaProperties.enabled(), mfaProperties.webauthnEnabled());
    var siem =
        new IdentitySecurityStatusResponse.Siem(
            siemProperties.pushEnabled(),
            siemProperties.effectiveProvider(),
            siemProperties.workerEnabled(),
            siemEndpointConfigured(siemProperties),
            siemSecretConfigured(siemProperties));
    return new IdentitySecurityStatusResponse(sso, scim, mfa, siem, false, null);
  }

  private static boolean siemEndpointConfigured(SiemProperties p) {
    if (!p.pushEnabled()) {
      return false;
    }
    if (!"generic-http".equals(p.effectiveProvider())) {
      return true;
    }
    return p.endpointUrl() != null && !p.endpointUrl().isBlank();
  }

  private static boolean siemSecretConfigured(SiemProperties p) {
    if (!p.pushEnabled()) {
      return false;
    }
    if ("noop".equals(p.effectiveProvider())) {
      return false;
    }
    return switch (p.effectiveAuthMode()) {
      case "none" -> true;
      case "bearer" -> p.bearerToken() != null && !p.bearerToken().isBlank();
      case "header" ->
          p.customHeaderValue() != null && !p.customHeaderValue().isBlank();
      default -> false;
    };
  }
}
