package com.notebook.lumen.common.security.servicejwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ServiceJwtVerifier {
  private final TrustedServiceProperties trustedService;
  private final RSASSAVerifier verifier;
  private final Clock clock;

  public ServiceJwtVerifier(TrustedServiceProperties trustedService) {
    this(trustedService, Clock.systemUTC());
  }

  public ServiceJwtVerifier(TrustedServiceProperties trustedService, Clock clock) {
    this.trustedService = trustedService;
    this.verifier =
        new RSASSAVerifier(
            RsaPemUtils.loadPublicKey(
                "TRUSTED_SERVICE_PUBLIC_KEY",
                trustedService.publicKey(),
                trustedService.publicKeyPath()));
    this.clock = clock;
  }

  public ServiceJwtClaims verify(String token, String requiredScope) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      validateHeader(jwt);
      if (!jwt.verify(verifier)) {
        throw invalid("INVALID_SERVICE_JWT", "Invalid service JWT signature");
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      validateClaims(claims, requiredScope);
      return new ServiceJwtClaims(
          claims.getIssuer(),
          claims.getSubject(),
          claims.getAudience().getFirst(),
          claims.getStringClaim("scope"),
          claims.getIssueTime().toInstant(),
          claims.getExpirationTime().toInstant(),
          UUID.fromString(claims.getJWTID()),
          claims.getStringClaim("service_name"));
    } catch (ServiceJwtValidationException e) {
      throw e;
    } catch (Exception e) {
      throw invalid("INVALID_SERVICE_JWT", "Invalid service JWT");
    }
  }

  private void validateHeader(SignedJWT jwt) {
    if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
      throw invalid("INVALID_SERVICE_JWT", "Service JWT must use RS256");
    }
    if (trustedService.kid() != null
        && !trustedService.kid().isBlank()
        && !trustedService.kid().equals(jwt.getHeader().getKeyID())) {
      throw invalid("INVALID_SERVICE_JWT", "Unknown service JWT kid");
    }
  }

  private void validateClaims(JWTClaimsSet claims, String requiredScope) throws Exception {
    if (!trustedService.issuer().equals(claims.getIssuer())) {
      throw invalid("INVALID_SERVICE_ISSUER", "Invalid service JWT issuer");
    }
    List<String> audiences = claims.getAudience();
    if (audiences == null || !audiences.contains(trustedService.audience())) {
      throw invalid("INVALID_SERVICE_AUDIENCE", "Invalid service JWT audience");
    }
    if (!"service".equals(claims.getStringClaim("token_type"))) {
      throw invalid("INVALID_SERVICE_JWT", "Invalid service JWT token type");
    }
    if (claims.getJWTID() == null || claims.getJWTID().isBlank()) {
      throw invalid("INVALID_SERVICE_JWT", "Service JWT jti is required");
    }
    if (claims.getExpirationTime() == null) {
      throw invalid("INVALID_SERVICE_JWT", "Service JWT exp is required");
    }
    validateExpiration(claims.getExpirationTime());
    validateScope(claims.getStringClaim("scope"), requiredScope);
  }

  private void validateExpiration(Date expiresAt) {
    Duration skew =
        trustedService.clockSkew() == null ? Duration.ofSeconds(5) : trustedService.clockSkew();
    Instant now = Instant.now(clock);
    if (expiresAt.toInstant().plus(skew).isBefore(now)) {
      throw invalid("EXPIRED_SERVICE_JWT", "Expired service JWT");
    }
  }

  private void validateScope(String scope, String requiredScope) {
    Set<String> scopes = scopeSet(scope);
    if (trustedService.allowedScopes() != null && !trustedService.allowedScopes().isEmpty()) {
      Set<String> allowed = new HashSet<>(trustedService.allowedScopes());
      if (scopes.stream().noneMatch(allowed::contains)) {
        throw insufficientScope();
      }
    }
    if (requiredScope != null && !requiredScope.isBlank() && !scopes.contains(requiredScope)) {
      throw insufficientScope();
    }
  }

  private Set<String> scopeSet(String scope) {
    if (scope == null || scope.isBlank()) {
      return Set.of();
    }
    return Set.of(scope.trim().split("\\s+"));
  }

  private ServiceJwtValidationException invalid(String code, String message) {
    return new ServiceJwtValidationException(code, message);
  }

  private ServiceJwtValidationException insufficientScope() {
    return new ServiceJwtValidationException(
        "INSUFFICIENT_SERVICE_SCOPE", "Insufficient service JWT scope", true);
  }
}
