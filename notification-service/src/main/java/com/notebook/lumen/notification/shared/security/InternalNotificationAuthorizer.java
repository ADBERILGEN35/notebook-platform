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
    NotificationProperties.TrustedService trustedService =
        properties.internal().trustedNotificationClient();
    if (!trustedService.configured()) {
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Trusted service is not configured");
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
    } catch (ServiceJwtValidationException e) {
      if (e.insufficientScope()) {
        throw new NotificationException(
            HttpStatus.FORBIDDEN, "NOTIFICATION_ACCESS_DENIED", e.getMessage());
      }
      throw new NotificationException(HttpStatus.UNAUTHORIZED, e.errorCode(), e.getMessage());
    } catch (RuntimeException e) {
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Invalid service JWT");
    }
  }

  private String bearerToken(String value) {
    if (!value.startsWith("Bearer ")) {
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Service authorization must be Bearer");
    }
    return value.substring("Bearer ".length()).trim();
  }
}
