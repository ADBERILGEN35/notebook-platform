package com.notebook.lumen.search.reindex.security;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtClaims;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtValidationException;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtVerifier;
import com.notebook.lumen.common.security.servicejwt.TrustedServiceProperties;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ReindexAuthorizer {
  public static final String HEADER_NAME = "X-Service-Authorization";
  public static final String MANAGE_SCOPE = "internal:search:reindex:manage";

  private final SearchProperties properties;

  public ReindexAuthorizer(SearchProperties properties) {
    this.properties = properties;
  }

  public ServiceJwtClaims authorize(String serviceAuthorization) {
    return authorize(serviceAuthorization, "REINDEX_ACCESS_DENIED");
  }

  public ServiceJwtClaims authorizeOrphanPreview(String serviceAuthorization) {
    return authorize(serviceAuthorization, "ORPHAN_PREVIEW_ACCESS_DENIED");
  }

  private ServiceJwtClaims authorize(String serviceAuthorization, String accessDeniedCode) {
    if (serviceAuthorization == null || serviceAuthorization.isBlank()) {
      throw new SearchException(
          HttpStatus.UNAUTHORIZED, accessDeniedCode, "Service JWT is required");
    }
    SearchProperties.TrustedService trustedService = properties.internal().trustedReindexClient();
    if (trustedService == null || !trustedService.configured()) {
      throw new SearchException(
          HttpStatus.UNAUTHORIZED,
          "INVALID_SERVICE_JWT",
          "Trusted reindex client is not configured");
    }
    try {
      return new ServiceJwtVerifier(
              new TrustedServiceProperties(
                  trustedService.kid(),
                  trustedService.publicKey(),
                  trustedService.publicKeyPath(),
                  trustedService.issuer(),
                  trustedService.audience(),
                  trustedService.clockSkew(),
                  trustedService.allowedScopeSet()))
          .verify(bearerToken(serviceAuthorization), MANAGE_SCOPE);
    } catch (ServiceJwtValidationException e) {
      if (e.insufficientScope()) {
        throw new SearchException(HttpStatus.FORBIDDEN, accessDeniedCode, e.getMessage());
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
