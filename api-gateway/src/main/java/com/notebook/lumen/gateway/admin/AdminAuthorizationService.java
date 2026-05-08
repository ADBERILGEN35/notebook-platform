package com.notebook.lumen.gateway.admin;

import com.notebook.lumen.gateway.config.GatewayAdminProperties;
import java.util.Collection;
import java.util.Locale;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthorizationService {
  private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

  private final GatewayAdminProperties properties;

  public AdminAuthorizationService(GatewayAdminProperties properties) {
    this.properties = properties;
  }

  public boolean isAdmin(Jwt jwt) {
    if (jwt == null) {
      return false;
    }
    if (hasPlatformAdminRole(jwt)) {
      return true;
    }
    String userId = jwt.getSubject();
    if (userId != null && properties.allowedUserIdSet().contains(userId)) {
      return true;
    }
    String email = jwt.getClaimAsString("email");
    return email != null
        && properties.allowedEmailSet().contains(email.toLowerCase(Locale.ROOT).trim());
  }

  public boolean adminFeatureEnabled() {
    return properties.enabled() && properties.effectiveAudit().enabled();
  }

  @SuppressWarnings("unchecked")
  private boolean hasPlatformAdminRole(Jwt jwt) {
    Object roles = jwt.getClaims().get("roles");
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
}
