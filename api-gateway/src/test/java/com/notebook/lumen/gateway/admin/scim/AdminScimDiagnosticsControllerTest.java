package com.notebook.lumen.gateway.admin.scim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
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

class AdminScimDiagnosticsControllerTest {

  @Test
  void compatibilityStatusRequiresScimDiagnosticsPermission() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    AdminScimDiagnosticsProxyService proxy = mock(AdminScimDiagnosticsProxyService.class);
    when(auth.ensureScimDiagnosticsRead(any()))
        .thenReturn(Optional.of(ErrorCode.ADMIN_PERMISSION_REQUIRED));
    var controller = new AdminScimDiagnosticsController(auth, proxy);

    StepVerifier.create(controller.compatibilityStatus(jwt(), "req-1"))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
              ErrorResponse body = (ErrorResponse) response.getBody();
              assertThat(body.errorCode()).isEqualTo(ErrorCode.ADMIN_PERMISSION_REQUIRED.name());
              assertThat(body.details()).containsEntry("permission", "admin:scim:diagnostics:read");
            })
        .verifyComplete();
    verifyNoInteractions(proxy);
  }

  @Test
  void syncRunsProxiesWhenAllowed() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    AdminScimDiagnosticsProxyService proxy = mock(AdminScimDiagnosticsProxyService.class);
    when(auth.ensureScimDiagnosticsRead(any())).thenReturn(Optional.empty());
    when(proxy.syncRuns(any(), any(), any(), any(), any(), eq(1), eq(20), eq("req-2"), any()))
        .thenReturn(Mono.just(ResponseEntity.ok(Map.of("items", java.util.List.of()))));
    var controller = new AdminScimDiagnosticsController(auth, proxy);

    StepVerifier.create(
            controller.syncRuns(jwt(), "okta", "USER", "COMPLETED", null, null, 1, 20, "req-2"))
        .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK))
        .verifyComplete();
  }

  private static Jwt jwt() {
    return Jwt.withTokenValue("t")
        .headers(h -> h.put("alg", "none"))
        .subject("u")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("email", "a@b.com")
        .claim("token_type", "access")
        .build();
  }
}
