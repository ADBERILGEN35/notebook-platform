package com.notebook.lumen.content.search.outbox.security;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.shared.exception.ContentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SearchOutboxAuthorizer {
  public static final String HEADER_NAME = "X-Service-Authorization";
  public static final String READ_SCOPE = "internal:content:search-outbox:read";
  public static final String MANAGE_SCOPE = "internal:content:search-outbox:manage";

  private final ContentProperties properties;

  public SearchOutboxAuthorizer(ContentProperties properties) {
    this.properties = properties;
  }

  public void authorize(String serviceAuthorization, String requiredScope) {
    if (serviceAuthorization == null || serviceAuthorization.isBlank()) {
      throw new ContentException(
          HttpStatus.UNAUTHORIZED,
          "INTERNAL_AUTH_REQUIRED",
          "Internal search outbox auth is required");
    }
    ContentProperties.SearchOutboxAdmin admin = admin();
    if (admin == null || !admin.configured()) {
      throw new ContentException(
          HttpStatus.UNAUTHORIZED,
          "INVALID_SERVICE_JWT",
          "Search outbox service trust is not configured");
    }
    try {
      new ServiceJwtVerifier(
              new TrustedServiceProperties(
                  admin.kid(),
                  admin.publicKey(),
                  admin.publicKeyPath(),
                  admin.issuer(),
                  admin.audience(),
                  admin.clockSkew(),
                  admin.allowedScopeSet()))
          .verify(bearerToken(serviceAuthorization), requiredScope);
    } catch (ServiceJwtValidationException e) {
      if (e.insufficientScope()) {
        throw new ContentException(
            HttpStatus.FORBIDDEN, "SEARCH_OUTBOX_ACCESS_DENIED", e.getMessage());
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

  private ContentProperties.SearchOutboxAdmin admin() {
    if (properties.search() == null || properties.search().outbox() == null) {
      return null;
    }
    return properties.search().outbox().admin();
  }
}
