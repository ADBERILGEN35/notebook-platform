package com.notebook.lumen.identity.audit;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuditAdminAuthorizer {
  public static final String HEADER_NAME = "X-Service-Authorization";
  public static final String REQUIRED_SCOPE = "internal:audit:read";
  public static final String ADMIN_STATUS_SCOPE = "internal:admin:status:read";
  public static final String CHANGE_REQUEST_SCOPE = "internal:admin:change-requests:manage";
  public static final String RBAC_READ_SCOPE = "internal:admin:rbac:read";
  public static final String RBAC_OVERRIDE_RELOAD_SCOPE = "internal:admin:rbac:overrides:reload";
  public static final String SCIM_DIAGNOSTICS_READ_SCOPE = "internal:admin:scim:diagnostics:read";
  public static final String BREAK_GLASS_EVENTS_READ_SCOPE =
      "internal:admin:break-glass:events:read";
  public static final String BREAK_GLASS_EVENTS_REVIEW_SCOPE =
      "internal:admin:break-glass:events:review";
  public static final String BREAK_GLASS_TOKEN_CHECK_SCOPE = "internal:break-glass:token:check";
  public static final String BREAK_GLASS_ROTATION_READ_SCOPE =
      "internal:admin:break-glass:rotation:read";
  public static final String BREAK_GLASS_ROTATION_MANAGE_SCOPE =
      "internal:admin:break-glass:rotation:manage";

  private final AuditAdminProperties properties;

  public AuditAdminAuthorizer(AuditAdminProperties properties) {
    this.properties = properties;
  }

  public void authorize(String serviceAuthorization) {
    authorize(serviceAuthorization, REQUIRED_SCOPE);
  }

  public void authorize(String serviceAuthorization, String requiredScope) {
    if (serviceAuthorization == null || serviceAuthorization.isBlank()) {
      throw new AuditAccessException(
          HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_REQUIRED", "Internal audit auth is required");
    }
    if (!properties.configured()) {
      throw new AuditAccessException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Audit service trust is not configured");
    }
    try {
      new ServiceJwtVerifier(
              new TrustedServiceProperties(
                  properties.kid(),
                  properties.publicKey(),
                  properties.publicKeyPath(),
                  properties.issuer(),
                  properties.audience(),
                  properties.clockSkew(),
                  properties.allowedScopeSet()))
          .verify(bearerToken(serviceAuthorization), requiredScope);
    } catch (ServiceJwtValidationException e) {
      if (e.insufficientScope()) {
        throw new AuditAccessException(HttpStatus.FORBIDDEN, "AUDIT_ACCESS_DENIED", e.getMessage());
      }
      throw new AuditAccessException(HttpStatus.UNAUTHORIZED, e.errorCode(), e.getMessage());
    } catch (RuntimeException e) {
      throw new AuditAccessException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Invalid service JWT");
    }
  }

  private String bearerToken(String value) {
    if (!value.startsWith("Bearer ")) {
      throw new AuditAccessException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Service authorization must be Bearer");
    }
    return value.substring("Bearer ".length()).trim();
  }
}
