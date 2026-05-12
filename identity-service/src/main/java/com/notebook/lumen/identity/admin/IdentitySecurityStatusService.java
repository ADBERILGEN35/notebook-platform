package com.notebook.lumen.identity.admin;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.breakglass.BreakGlassService;
import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.siem.SiemProperties;
import com.notebook.lumen.identity.sso.SsoProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IdentitySecurityStatusService {
  private final SsoProperties ssoProperties;
  private final ScimProperties scimProperties;
  private final MfaProperties mfaProperties;
  private final SiemProperties siemProperties;
  private final AdminRbacService adminRbacService;
  private final BreakGlassService breakGlassService;

  public IdentitySecurityStatusService(
      SsoProperties ssoProperties,
      ScimProperties scimProperties,
      MfaProperties mfaProperties,
      SiemProperties siemProperties,
      AdminRbacService adminRbacService,
      BreakGlassService breakGlassService) {
    this.ssoProperties = ssoProperties;
    this.scimProperties = scimProperties;
    this.mfaProperties = mfaProperties;
    this.siemProperties = siemProperties;
    this.adminRbacService = adminRbacService;
    this.breakGlassService = breakGlassService;
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
            scimProperties.authConfigured(),
            scimProperties.providerType(),
            scimProperties.deltaSyncEnabled(),
            scimProperties.deltaSyncMode(),
            scimProperties.providerSupportsBulk(),
            scimProperties.providerSupportsFiltering(),
            scimProperties.providerSupportsPatch(),
            scimProperties.providerSupportsNestedGroups(),
            scimProperties.providerRateLimitAware(),
            Math.max(1, scimProperties.providerMaxPageSize()));
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
    var adminRbac = mapAdminRbac(adminRbacService.statusSnapshot());
    var bg = breakGlassService.status();
    var breakGlass =
        new IdentitySecurityStatusResponse.BreakGlass(
            bg.enabled(),
            bg.credentialMode(),
            bg.allowedModes(),
            bg.staticTokenConfigured(),
            bg.webauthnEnabled(),
            bg.webauthnCredentialCount(),
            bg.offlineSignedEnabled(),
            bg.offlinePublicKeyConfigured(),
            bg.staticTokenRotationRecommended(),
            bg.lastStaticTokenUsedAt(),
            bg.approvalMode(),
            bg.pendingReviewCount(),
            bg.overdueReviewCount(),
            bg.sessionTtlMinutes(),
            bg.maxActiveSessions(),
            bg.requireReason(),
            bg.requireMfa(),
            bg.rotationTrackingEnabled(),
            bg.rotationRequired(),
            bg.openRotationEvents(),
            bg.oldestRotationRequiredAt(),
            bg.lastRotationVerifiedAt());
    return new IdentitySecurityStatusResponse(
        sso, scim, mfa, siem, adminRbac, breakGlass, false, null);
  }

  private static IdentitySecurityStatusResponse.AdminRbac mapAdminRbac(
      AdminRbacService.AdminRbacStatusSnapshot s) {
    Map<String, Boolean> roles = new LinkedHashMap<>();
    roles.put(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN, s.platformAdmin());
    roles.put(PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER, s.auditViewer());
    roles.put(PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_EXPORTER, s.auditExporter());
    roles.put(PlatformAdminRbacConstants.ROLE_PLATFORM_SECURITY_ADMIN, s.securityAdmin());
    roles.put(PlatformAdminRbacConstants.ROLE_PLATFORM_IDENTITY_ADMIN, s.identityAdmin());
    roles.put(
        PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_AUTHOR, s.changeRequestAuthor());
    roles.put(
        PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_APPROVER,
        s.changeRequestApprover());
    roles.put(
        PlatformAdminRbacConstants.ROLE_PLATFORM_OBSERVABILITY_VIEWER, s.observabilityViewer());
    return new IdentitySecurityStatusResponse.AdminRbac(
        s.enabled(), s.legacyPlatformAdminImpliesAll(), roles);
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
      case "header" -> p.customHeaderValue() != null && !p.customHeaderValue().isBlank();
      default -> false;
    };
  }
}
