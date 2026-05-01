package com.notebook.lumen.content.config;

import com.notebook.lumen.common.security.secrets.SecretValue;
import com.notebook.lumen.common.security.servicejwt.InternalAuthMode;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import java.time.Duration;
import org.springframework.http.HttpHeaders;

final class WorkspaceInternalAuthHeaders {
  private final ContentProperties properties;
  private final InternalAuthMode authMode;
  private final SecretValue internalToken;
  private final ServiceJwtSigner serviceJwtSigner;

  WorkspaceInternalAuthHeaders(ContentProperties properties) {
    this.properties = properties;
    this.authMode = InternalAuthMode.parse(properties.workspace().internalAuthMode());
    this.internalToken = properties.workspace().effectiveInternalApiTokenSecret();
    this.serviceJwtSigner = serviceJwtSigner(properties, authMode);
  }

  void apply(HttpHeaders headers, String path) {
    if (serviceJwtSigner != null) {
      headers.set(
          "X-Service-Authorization",
          "Bearer " + serviceJwtSigner.sign(properties.serviceJwt().audience(), scopeFor(path)));
    } else if (authMode.acceptsStaticToken() && internalToken.hasText()) {
      headers.set("X-Internal-Token", internalToken.value());
    }
  }

  private ServiceJwtSigner serviceJwtSigner(ContentProperties properties, InternalAuthMode mode) {
    ContentProperties.ServiceJwt jwt = properties.serviceJwt();
    if (!mode.acceptsServiceJwt()) {
      return null;
    }
    if (jwt == null || !jwt.signingConfigured()) {
      if (mode == InternalAuthMode.SERVICE_JWT) {
        throw new IllegalStateException(
            "INTERNAL_SERVICE_JWT_PRIVATE_KEY_PATH is required in service-jwt mode.");
      }
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

  private String scopeFor(String path) {
    if (path.contains("/tags/")) {
      return "internal:workspace:tag:read";
    }
    return "internal:workspace:permission:read";
  }
}
