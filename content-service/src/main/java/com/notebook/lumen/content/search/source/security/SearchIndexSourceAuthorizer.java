package com.notebook.lumen.content.search.source.security;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.shared.exception.ContentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexSourceAuthorizer {
  public static final String HEADER_NAME = "X-Service-Authorization";
  public static final String REQUIRED_SCOPE = "internal:content:search-index-source:read";

  private final ContentProperties properties;

  public SearchIndexSourceAuthorizer(ContentProperties properties) {
    this.properties = properties;
  }

  public void authorize(String serviceAuthorization) {
    if (serviceAuthorization == null || serviceAuthorization.isBlank()) {
      throw new ContentException(
          HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_REQUIRED", "Service JWT is required");
    }
    ContentProperties.TrustedService trustedService = trustedService();
    if (trustedService == null || !trustedService.configured()) {
      throw new ContentException(
          HttpStatus.UNAUTHORIZED,
          "INVALID_SERVICE_JWT",
          "Trusted search service is not configured");
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
        throw new ContentException(
            HttpStatus.FORBIDDEN, "SEARCH_INDEX_SOURCE_ACCESS_DENIED", e.getMessage());
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

  private ContentProperties.TrustedService trustedService() {
    if (properties.search() == null || properties.search().source() == null) {
      return null;
    }
    return properties.search().source().trustedSearchService();
  }
}
