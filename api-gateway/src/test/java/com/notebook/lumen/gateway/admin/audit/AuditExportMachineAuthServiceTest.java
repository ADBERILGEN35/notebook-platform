package com.notebook.lumen.gateway.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.gateway.config.GatewayAuditExportProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AuditExportMachineAuthServiceTest {

  @Test
  void acceptsValidMachineToken() {
    AuditExportMachineAuthService service = new AuditExportMachineAuthService(props(true));
    var result =
        service.validate(
            jwt(
                "machine",
                "https://audit-exporter.internal",
                "admin:audit:export",
                600,
                "api-gateway"));
    assertThat(result.success()).isTrue();
    assertThat(result.principalId()).isEqualTo("audit-exporter");
  }

  @Test
  void rejectsWrongIssuer() {
    AuditExportMachineAuthService service = new AuditExportMachineAuthService(props(true));
    var result =
        service.validate(
            jwt(
                "machine",
                "https://bad-issuer.internal",
                "admin:audit:export",
                600,
                "api-gateway"));
    assertThat(result.success()).isFalse();
    assertThat(result.errorCode().name()).isEqualTo("INVALID_AUDIT_EXPORT_MACHINE_ISSUER");
  }

  @Test
  void rejectsMissingScope() {
    AuditExportMachineAuthService service = new AuditExportMachineAuthService(props(true));
    var result =
        service.validate(
            jwt(
                "machine",
                "https://audit-exporter.internal",
                "admin:audit:read",
                600,
                "api-gateway"));
    assertThat(result.success()).isFalse();
    assertThat(result.errorCode().name()).isEqualTo("INSUFFICIENT_AUDIT_EXPORT_MACHINE_SCOPE");
  }

  @Test
  void rejectsTtlTooLong() {
    AuditExportMachineAuthService service = new AuditExportMachineAuthService(props(true));
    var result =
        service.validate(
            jwt(
                "machine",
                "https://audit-exporter.internal",
                "admin:audit:export",
                2000,
                "api-gateway"));
    assertThat(result.success()).isFalse();
    assertThat(result.errorCode().name()).isEqualTo("AUDIT_EXPORT_MACHINE_TOKEN_TTL_TOO_LONG");
  }

  private GatewayAuditExportProperties props(boolean enabled) {
    return new GatewayAuditExportProperties(
        true,
        31,
        10000,
        200,
        new GatewayAuditExportProperties.MachineAuth(
            enabled,
            "https://audit-exporter.internal",
            "api-gateway",
            "admin:audit:export",
            "/tmp/public.pem",
            "",
            900),
        false,
        false,
        "");
  }

  private Jwt jwt(String tokenType, String issuer, String scope, int ttlSeconds, String aud) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("audit-exporter")
        .issuer(issuer)
        .audience(List.of(aud))
        .issuedAt(now)
        .expiresAt(now.plusSeconds(ttlSeconds))
        .claim("scope", scope)
        .claim("token_type", tokenType)
        .claim("jti", "jti-1")
        .build();
  }
}
