package com.notebook.lumen.workspace.config.notification;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import java.time.Duration;
import org.springframework.http.HttpHeaders;

final class NotificationServiceAuthHeaders {
  private static final String REQUIRED_SCOPE = "internal:notification:email:send";

  private final WorkspaceProperties properties;
  private final ServiceJwtSigner signer;

  NotificationServiceAuthHeaders(WorkspaceProperties properties) {
    this.properties = properties;
    this.signer = signer(properties.notification().serviceJwt());
  }

  void apply(HttpHeaders headers) {
    if (signer != null) {
      headers.set(
          "X-Service-Authorization",
          "Bearer "
              + signer.sign(properties.notification().serviceJwt().audience(), REQUIRED_SCOPE));
    }
  }

  private ServiceJwtSigner signer(WorkspaceProperties.ServiceJwt jwt) {
    if (jwt == null || !jwt.signingConfigured()) {
      return null;
    }
    return new ServiceJwtSigner(
        new ServiceJwtProperties(
            jwt.activeKid(),
            jwt.privateKey(),
            jwt.privateKeyPath(),
            jwt.issuer(),
            jwt.subject(),
            jwt.serviceName(),
            Duration.ofSeconds(jwt.ttlSeconds() <= 0 ? 60 : jwt.ttlSeconds())));
  }
}
