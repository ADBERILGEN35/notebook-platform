package com.notebook.lumen.gateway.admin.audit;

import com.notebook.lumen.gateway.config.GatewayAuditExportProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuditExportMachineAuthService {
  private final GatewayAuditExportProperties properties;

  public AuditExportMachineAuthService(GatewayAuditExportProperties properties) {
    this.properties = properties;
    GatewayAuditExportProperties.MachineAuth machineAuth = properties.effectiveMachineAuth();
    if (machineAuth.enabled()) {
      if (machineAuth.allowedIssuers() == null || machineAuth.allowedIssuers().isBlank()) {
        throw new IllegalStateException(
            "gateway.admin.audit-export.machine-auth.allowed-issuers must be configured");
      }
      if (machineAuth.requiredScope() == null || machineAuth.requiredScope().isBlank()) {
        throw new IllegalStateException(
            "gateway.admin.audit-export.machine-auth.required-scope must be configured");
      }
      if (machineAuth.audience() == null || machineAuth.audience().isBlank()) {
        throw new IllegalStateException(
            "gateway.admin.audit-export.machine-auth.audience must be configured");
      }
      if ((machineAuth.publicKeyPath() == null || machineAuth.publicKeyPath().isBlank())
          && (machineAuth.publicKey() == null || machineAuth.publicKey().isBlank())) {
        throw new IllegalStateException(
            "gateway.admin.audit-export.machine-auth.public-key-path or public-key must be configured");
      }
    }
  }

  public boolean isMachineToken(Jwt jwt) {
    return jwt != null && "machine".equals(jwt.getClaimAsString("token_type"));
  }

  public ValidationResult validate(Jwt jwt) {
    GatewayAuditExportProperties.MachineAuth machineAuth = properties.effectiveMachineAuth();
    if (!machineAuth.enabled()) {
      return ValidationResult.fail(
          HttpStatus.FORBIDDEN,
          ErrorCode.AUDIT_EXPORT_MACHINE_AUTH_DISABLED,
          "Machine auth is disabled for audit export");
    }
    if (jwt == null) {
      return ValidationResult.fail(
          HttpStatus.UNAUTHORIZED,
          ErrorCode.AUDIT_EXPORT_MACHINE_TOKEN_REQUIRED,
          "Machine token is required");
    }
    if (!"machine".equals(jwt.getClaimAsString("token_type"))) {
      return ValidationResult.fail(
          HttpStatus.UNAUTHORIZED,
          ErrorCode.INVALID_AUDIT_EXPORT_MACHINE_TOKEN,
          "Invalid machine token type");
    }

    Set<String> allowedIssuers =
        Arrays.stream(machineAuth.allowedIssuers().split(","))
            .map(String::trim)
            .filter(v -> !v.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
    if (issuer == null || !allowedIssuers.contains(issuer)) {
      return ValidationResult.fail(
          HttpStatus.UNAUTHORIZED,
          ErrorCode.INVALID_AUDIT_EXPORT_MACHINE_ISSUER,
          "Invalid machine token issuer");
    }

    Collection<String> audience = jwt.getAudience();
    String expectedAudience = machineAuth.audience();
    if (expectedAudience == null
        || expectedAudience.isBlank()
        || audience == null
        || audience.stream().noneMatch(expectedAudience::equals)) {
      return ValidationResult.fail(
          HttpStatus.UNAUTHORIZED,
          ErrorCode.INVALID_AUDIT_EXPORT_MACHINE_AUDIENCE,
          "Invalid machine token audience");
    }

    if (!containsScope(jwt, machineAuth.requiredScope())) {
      return ValidationResult.fail(
          HttpStatus.FORBIDDEN,
          ErrorCode.INSUFFICIENT_AUDIT_EXPORT_MACHINE_SCOPE,
          "Insufficient machine token scope");
    }

    Instant issuedAt = jwt.getIssuedAt();
    Instant expiresAt = jwt.getExpiresAt();
    if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
      return ValidationResult.fail(
          HttpStatus.UNAUTHORIZED,
          ErrorCode.INVALID_AUDIT_EXPORT_MACHINE_TOKEN,
          "Machine token timestamps are invalid");
    }
    int maxTtl = machineAuth.maxTtlSeconds() <= 0 ? 900 : machineAuth.maxTtlSeconds();
    long ttlSeconds = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
    if (ttlSeconds > maxTtl) {
      return ValidationResult.fail(
          HttpStatus.UNAUTHORIZED,
          ErrorCode.AUDIT_EXPORT_MACHINE_TOKEN_TTL_TOO_LONG,
          "Machine token ttl exceeds allowed maximum");
    }
    if (expiresAt.isBefore(Instant.now())) {
      return ValidationResult.fail(
          HttpStatus.UNAUTHORIZED,
          ErrorCode.EXPIRED_AUDIT_EXPORT_MACHINE_TOKEN,
          "Machine token expired");
    }
    return ValidationResult.ok(
        jwt.getSubject(),
        issuer,
        resolveScope(jwt),
        jwt.getId() == null ? "" : jwt.getId().toLowerCase(Locale.ROOT));
  }

  private boolean containsScope(Jwt jwt, String requiredScope) {
    if (requiredScope == null || requiredScope.isBlank()) {
      return false;
    }
    String scope = resolveScope(jwt);
    if (scope.isBlank()) {
      return false;
    }
    return Arrays.stream(scope.split("\\s+")).anyMatch(requiredScope::equals);
  }

  private String resolveScope(Jwt jwt) {
    String scope = jwt.getClaimAsString("scope");
    if (scope != null && !scope.isBlank()) {
      return scope;
    }
    Object scopes = jwt.getClaims().get("scopes");
    if (scopes instanceof Collection<?> collection) {
      return collection.stream().map(String::valueOf).collect(Collectors.joining(" "));
    }
    return "";
  }

  public record ValidationResult(
      boolean success,
      HttpStatus status,
      ErrorCode errorCode,
      String message,
      String principalId,
      String issuer,
      String scope,
      String jti) {
    static ValidationResult ok(String principalId, String issuer, String scope, String jti) {
      return new ValidationResult(true, null, null, null, principalId, issuer, scope, jti);
    }

    static ValidationResult fail(HttpStatus status, ErrorCode errorCode, String message) {
      return new ValidationResult(false, status, errorCode, message, null, null, null, null);
    }
  }
}
