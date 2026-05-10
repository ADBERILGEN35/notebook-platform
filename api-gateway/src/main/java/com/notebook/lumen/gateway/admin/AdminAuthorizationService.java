package com.notebook.lumen.gateway.admin;

import com.notebook.lumen.gateway.config.GatewayAdminProperties;
import com.notebook.lumen.gateway.config.GatewayAdminWriteProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.util.Optional;
import java.util.Collection;
import java.util.Locale;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthorizationService {
  private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

  private final GatewayAdminProperties properties;
  private final GatewayAdminWriteProperties writeProperties;

  public AdminAuthorizationService(
      GatewayAdminProperties properties, GatewayAdminWriteProperties writeProperties) {
    this.properties = properties;
    this.writeProperties = writeProperties;
  }

  public boolean isAdmin(Jwt jwt) {
    if (jwt == null) {
      return false;
    }
    boolean requiresMfa = requiresMfa();
    if (hasPlatformAdminRole(jwt)) {
      if (requiresMfa && !hasVerifiedMfa(jwt)) {
        return false;
      }
      return true;
    }
    String userId = jwt.getSubject();
    if (userId != null && properties.allowedUserIdSet().contains(userId)) {
      return !requiresMfa || hasVerifiedMfa(jwt);
    }
    String email = jwt.getClaimAsString("email");
    boolean allowlisted =
        email != null
        && properties.allowedEmailSet().contains(email.toLowerCase(Locale.ROOT).trim());
    return allowlisted && (!requiresMfa || hasVerifiedMfa(jwt));
  }

  public boolean requiresMfa() {
    String mode = properties.effectiveMfaMode();
    return properties.requireMfa() || "enforce".equals(mode);
  }

  public boolean adminFeatureEnabled() {
    return properties.enabled() && properties.effectiveAudit().enabled();
  }

  public boolean enterpriseFeatureEnabled() {
    return properties.enabled() && properties.effectiveEnterprise().enabled();
  }

  public boolean adminWriteFeatureEnabled() {
    return enterpriseFeatureEnabled() && writeProperties.enabled();
  }

  /**
   * Enterprise admin change requests require {@code PLATFORM_ADMIN} (or equivalent role claim). Email /
   * user-id allowlists are not sufficient for write operations. MFA is required whenever MFA mode is
   * not {@code off} or {@code gateway.admin.require-mfa} is true.
   */
  public Optional<ErrorCode> enterpriseAdminWriteDenialReason(Jwt jwt) {
    if (jwt == null) {
      return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
    }
    if (!hasPlatformAdminRole(jwt)) {
      return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  private boolean adminWriteRequiresMfa() {
    return properties.requireMfa() || !"off".equals(properties.effectiveMfaMode());
  }

  @SuppressWarnings("unchecked")
  private boolean hasPlatformAdminRole(Jwt jwt) {
    return hasRoleClaim(jwt.getClaims().get("platform_roles")) || hasRoleClaim(jwt.getClaims().get("roles"));
  }

  private boolean hasRoleClaim(Object roles) {
    if (roles instanceof Collection<?> collection) {
      return collection.stream().map(String::valueOf).anyMatch(this::isPlatformAdminRole);
    }
    if (roles instanceof String value) {
      for (String role : value.split(",")) {
        if (isPlatformAdminRole(role)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isPlatformAdminRole(String role) {
    if (role == null) {
      return false;
    }
    String normalized = role.trim().toUpperCase(Locale.ROOT);
    return PLATFORM_ADMIN.equals(normalized)
        || "ROLE_PLATFORM_ADMIN".equals(normalized)
        || "ADMIN".equals(normalized)
        || "ROLE_ADMIN".equals(normalized);
  }

  @SuppressWarnings("unchecked")
  private boolean hasVerifiedMfa(Jwt jwt) {
    Object mfaVerified = jwt.getClaims().get("mfa_verified");
    if (mfaVerified instanceof Boolean bool && bool) {
      return true;
    }
    Object amr = jwt.getClaims().get("amr");
    if (amr instanceof Collection<?> collection) {
      java.util.Set<String> accepted = properties.acceptedMfaMethods();
      return collection.stream()
          .map(String::valueOf)
          .map(value -> value.toLowerCase(Locale.ROOT))
          .anyMatch(accepted::contains);
    }
    return false;
  }
}
