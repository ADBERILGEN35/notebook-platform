package com.notebook.lumen.gateway.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.config.GatewayPlatformRetentionProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AdminPlatformRetentionControllerTest {

  @Test
  void targetsRequireRetentionReadPermission() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    AdminPlatformRetentionProxyService proxy = mock(AdminPlatformRetentionProxyService.class);
    when(auth.ensurePlatformRetentionRead(any()))
        .thenReturn(Optional.of(ErrorCode.ADMIN_PERMISSION_REQUIRED));
    var controller =
        new AdminPlatformRetentionController(
            auth, new GatewayPlatformRetentionProperties(true, true), proxy);

    StepVerifier.create(controller.targets(jwt(false), "req-1"))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              ErrorResponse body = (ErrorResponse) response.getBody();
              assertThat(body.details()).containsEntry("permission", "admin:retention:read");
            })
        .verifyComplete();
    verifyNoInteractions(proxy);
  }

  @Test
  void legalHoldWriteRequiresMfaPermissionGate() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    AdminPlatformRetentionProxyService proxy = mock(AdminPlatformRetentionProxyService.class);
    when(auth.ensurePlatformLegalHoldWrite(any()))
        .thenReturn(Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED));
    var controller =
        new AdminPlatformRetentionController(
            auth, new GatewayPlatformRetentionProperties(true, true), proxy);

    StepVerifier.create(controller.createLegalHold(jwt(false), Map.of("reason", "valid reason"), "req-2"))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              ErrorResponse body = (ErrorResponse) response.getBody();
              assertThat(body.errorCode()).isEqualTo(ErrorCode.ADMIN_WRITE_MFA_REQUIRED.name());
            })
        .verifyComplete();
    verifyNoInteractions(proxy);
  }

  @Test
  void planProxiesWhenAllowed() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    AdminPlatformRetentionProxyService proxy = mock(AdminPlatformRetentionProxyService.class);
    when(auth.ensurePlatformRetentionRead(any())).thenReturn(Optional.empty());
    when(proxy.plan(null, true, "req-3", "/admin/retention/platform/plan"))
        .thenReturn(Mono.just(ResponseEntity.ok(Map.of("dryRun", true))));
    var controller =
        new AdminPlatformRetentionController(
            auth, new GatewayPlatformRetentionProperties(true, true), proxy);

    StepVerifier.create(controller.plan(jwt(false), null, true, "req-3"))
        .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK))
        .verifyComplete();
  }

  private static Jwt jwt(boolean mfa) {
    var builder =
        Jwt.withTokenValue("t")
            .headers(h -> h.put("alg", "none"))
            .subject("00000000-0000-0000-0000-000000000001")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("email", "admin@example.com")
            .claim("token_type", "access");
    if (mfa) {
      builder.claim("mfa_verified", true).claim("amr", java.util.List.of("pwd", "webauthn"));
    }
    return builder.build();
  }
}
