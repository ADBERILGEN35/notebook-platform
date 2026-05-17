package com.notebook.lumen.workspace.admin.retention;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceRetentionAdminAuthorizer {
  public static final String HEADER_NAME = "X-Service-Authorization";
  public static final String REQUIRED_SCOPE = "internal:admin:retention:read";

  private final WorkspaceRetentionAdminProperties properties;

  public WorkspaceRetentionAdminAuthorizer(WorkspaceRetentionAdminProperties properties) {
    this.properties = properties;
  }

  public void authorize(String serviceAuthorization) {
    if (serviceAuthorization == null || serviceAuthorization.isBlank()) {
      throw Exceptions.unauthorized(
          "INTERNAL_AUTH_REQUIRED", "Internal retention auth is required");
    }
    if (!properties.configured()) {
      throw Exceptions.unauthorized(
          "INVALID_SERVICE_JWT", "Workspace retention service trust is not configured");
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
        throw Exceptions.forbidden("RETENTION_ACCESS_DENIED", e.getMessage());
      }
      throw Exceptions.unauthorized(e.errorCode(), e.getMessage());
    } catch (RuntimeException e) {
      throw Exceptions.unauthorized("INVALID_SERVICE_JWT", "Invalid service JWT");
    }
  }

  private String bearerToken(String value) {
    if (!value.startsWith("Bearer ")) {
      throw Exceptions.unauthorized(
          "INVALID_SERVICE_JWT", "Service authorization must be Bearer");
    }
    return value.substring("Bearer ".length()).trim();
  }
}
