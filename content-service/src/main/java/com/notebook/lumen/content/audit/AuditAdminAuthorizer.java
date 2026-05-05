package com.notebook.lumen.content.audit;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import com.notebook.lumen.content.shared.exception.ContentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuditAdminAuthorizer {
  public static final String HEADER_NAME = "X-Service-Authorization";
  public static final String REQUIRED_SCOPE = "internal:audit:read";

  private final AuditAdminProperties properties;

  public AuditAdminAuthorizer(AuditAdminProperties properties) {
    this.properties = properties;
  }

  public void authorize(String serviceAuthorization) {
    if (serviceAuthorization == null || serviceAuthorization.isBlank()) {
      throw new ContentException(
          HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_REQUIRED", "Internal audit auth is required");
    }
    if (!properties.configured()) {
      throw new ContentException(
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
          .verify(bearerToken(serviceAuthorization), REQUIRED_SCOPE);
    } catch (ServiceJwtValidationException e) {
      if (e.insufficientScope()) {
        throw new ContentException(HttpStatus.FORBIDDEN, "AUDIT_ACCESS_DENIED", e.getMessage());
      }
      throw new ContentException(HttpStatus.UNAUTHORIZED, e.errorCode(), e.getMessage());
    } catch (RuntimeException e) {
      throw new ContentException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Invalid service JWT");
    }
  }

  private String bearerToken(String value) {
    if (!value.startsWith("Bearer ")) {
      throw new ContentException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Service authorization must be Bearer");
    }
    return value.substring("Bearer ".length()).trim();
  }
}
