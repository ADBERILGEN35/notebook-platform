package com.notebook.lumen.notification.shared.security;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InternalNotificationAuthorizer {
  public static final String HEADER_NAME = "X-Service-Authorization";
  public static final String REQUIRED_SCOPE = "internal:notification:email:send";

  private final NotificationProperties properties;

  public InternalNotificationAuthorizer(NotificationProperties properties) {
    this.properties = properties;
  }

  public void authorize(String serviceAuthorization) {
    if (serviceAuthorization == null || serviceAuthorization.isBlank()) {
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, "NOTIFICATION_ACCESS_DENIED", "Service JWT is required");
    }
    var trustedServices =
        java.util.Arrays.asList(
            properties.internal().trustedNotificationClient(),
            properties.internal().trustedIdentityClient());
    if (trustedServices.stream().noneMatch(service -> service != null && service.configured())) {
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Trusted service is not configured");
    }
    ServiceJwtValidationException validationFailure = null;
    RuntimeException runtimeFailure = null;
    for (NotificationProperties.TrustedService trustedService : trustedServices) {
      if (trustedService == null || !trustedService.configured()) {
        continue;
      }
      try {
        new ServiceJwtVerifier(
                new TrustedServiceProperties(
                    trustedService.kid(),
                    trustedService.publicKey(),
                    trustedService.publicKeyPath(),
                    trustedService.issuer(),
                    trustedService.audience(),
                    trustedService.clockSkew(),
                    trustedService.allowedScopeSet()))
            .verify(bearerToken(serviceAuthorization), REQUIRED_SCOPE);
        return;
      } catch (ServiceJwtValidationException e) {
        validationFailure = e;
      } catch (RuntimeException e) {
        runtimeFailure = e;
      }
    }
    if (validationFailure != null) {
      if (validationFailure.insufficientScope()) {
        throw new NotificationException(
            HttpStatus.FORBIDDEN, "NOTIFICATION_ACCESS_DENIED", validationFailure.getMessage());
      }
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, validationFailure.errorCode(), validationFailure.getMessage());
    }
    try {
      if (runtimeFailure != null) {
        throw runtimeFailure;
      }
    } catch (RuntimeException e) {
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Invalid service JWT");
    }
    throw new NotificationException(
        HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Invalid service JWT");
  }

  private String bearerToken(String value) {
    if (!value.startsWith("Bearer ")) {
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Service authorization must be Bearer");
    }
    return value.substring("Bearer ".length()).trim();
  }
}
