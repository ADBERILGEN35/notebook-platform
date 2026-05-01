package com.notebook.lumen.workspace.shared;

import com.notebook.lumen.common.security.secrets.SecretValue;
import com.notebook.lumen.common.security.servicejwt.InternalAuthMode;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class InternalApiTokenValidator {
  public static final String HEADER_NAME = "X-Internal-Token";
  public static final String SERVICE_AUTH_HEADER_NAME = "X-Service-Authorization";

  private final WorkspaceProperties properties;

  public InternalApiTokenValidator(WorkspaceProperties properties) {
    this.properties = properties;
  }

  public void validate(String providedToken, String serviceAuthorization, String requiredScope) {
    WorkspaceProperties.Internal internal = properties.internal();
    if (internal == null) {
      return;
    }
    InternalAuthMode mode = InternalAuthMode.parse(internal.authMode());
    if (mode.acceptsServiceJwt() && hasText(serviceAuthorization)) {
      validateServiceJwt(internal, serviceAuthorization, requiredScope);
      return;
    }
    if (mode == InternalAuthMode.SERVICE_JWT) {
      throw Exceptions.unauthorized("INTERNAL_AUTH_REQUIRED", "Service JWT is required");
    }
    if (mode == InternalAuthMode.DUAL
        && internal.trustedContentService() != null
        && internal.trustedContentService().configured()
        && !internal.tokenRequired()) {
      throw Exceptions.unauthorized(
          "INTERNAL_AUTH_REQUIRED", "Internal authentication is required");
    }
    validateStaticToken(internal, providedToken);
  }

  private void validateStaticToken(WorkspaceProperties.Internal internal, String providedToken) {
    if (!internal.tokenRequired()) {
      return;
    }
    if (!hasText(providedToken)) {
      throw Exceptions.unauthorized(
          "INTERNAL_AUTH_REQUIRED", "Internal authentication is required");
    }
    SecretValue primary = internal.effectivePrimarySecret();
    SecretValue secondary = internal.secondarySecret();
    if (!matches(providedToken, primary.value())
        && (!internal.secondaryTokenConfigured() || !matches(providedToken, secondary.value()))) {
      throw Exceptions.unauthorized("INVALID_INTERNAL_TOKEN", "Invalid internal API token");
    }
  }

  private void validateServiceJwt(
      WorkspaceProperties.Internal internal, String serviceAuthorization, String requiredScope) {
    String token = bearerToken(serviceAuthorization);
    WorkspaceProperties.TrustedService trusted = internal.trustedContentService();
    if (trusted == null || !trusted.configured()) {
      throw Exceptions.unauthorized("INVALID_SERVICE_JWT", "Trusted service key is not configured");
    }
    try {
      ServiceJwtVerifier verifier =
          new ServiceJwtVerifier(
              new TrustedServiceProperties(
                  trusted.kid(),
                  trusted.publicKey(),
                  trusted.publicKeyPath(),
                  trusted.issuer(),
                  trusted.audience(),
                  Duration.ofSeconds(trusted.clockSkewSeconds()),
                  trusted.allowedScopeSet()));
      verifier.verify(token, requiredScope);
    } catch (ServiceJwtValidationException e) {
      if (e.insufficientScope()) {
        throw Exceptions.forbidden(e.errorCode(), e.getMessage());
      }
      throw Exceptions.unauthorized(e.errorCode(), e.getMessage());
    } catch (RuntimeException e) {
      throw Exceptions.unauthorized("INVALID_SERVICE_JWT", "Invalid service JWT");
    }
  }

  private String bearerToken(String value) {
    if (value == null || !value.startsWith("Bearer ")) {
      throw Exceptions.unauthorized("INVALID_SERVICE_JWT", "Service authorization must be Bearer");
    }
    return value.substring("Bearer ".length()).trim();
  }

  private boolean matches(String providedToken, String expectedToken) {
    if (providedToken == null || expectedToken == null || expectedToken.isBlank()) {
      return false;
    }
    byte[] provided = providedToken.getBytes(StandardCharsets.UTF_8);
    byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(provided, expected);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
