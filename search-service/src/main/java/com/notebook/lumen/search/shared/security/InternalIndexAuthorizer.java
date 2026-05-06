package com.notebook.lumen.search.shared.security;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InternalIndexAuthorizer {
  public static final String HEADER_NAME = "X-Service-Authorization";
  public static final String REQUIRED_SCOPE = "internal:search:index:write";

  private final SearchProperties properties;

  public InternalIndexAuthorizer(SearchProperties properties) {
    this.properties = properties;
  }

  public void authorize(String serviceAuthorization) {
    if (serviceAuthorization == null || serviceAuthorization.isBlank()) {
      throw new SearchException(
          HttpStatus.UNAUTHORIZED, "SEARCH_ACCESS_DENIED", "Service JWT is required");
    }
    SearchProperties.TrustedService trustedService = properties.internal().trustedIndexingClient();
    if (trustedService == null || !trustedService.configured()) {
      throw new SearchException(
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
        throw new SearchException(HttpStatus.FORBIDDEN, "SEARCH_ACCESS_DENIED", e.getMessage());
      }
      throw new SearchException(HttpStatus.UNAUTHORIZED, e.errorCode(), e.getMessage());
    } catch (RuntimeException e) {
      throw new SearchException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Invalid service JWT");
    }
  }

  private String bearerToken(String value) {
    if (!value.startsWith("Bearer ")) {
      throw new SearchException(
          HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_JWT", "Service authorization must be Bearer");
    }
    return value.substring("Bearer ".length()).trim();
  }
}
