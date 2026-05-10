package com.notebook.lumen.gateway.admin.enterprise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AdminEnterpriseChangeRequestControllerDirectTest {

  @Test
  void writeDisabled_returns404() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    when(auth.adminWriteFeatureEnabled()).thenReturn(false);
    var controller =
        new AdminEnterpriseChangeRequestController(auth, mock(EnterpriseChangeRequestProxyService.class));

    StepVerifier.create(controller.list(jwtPlatformAdmin(), null, null))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(((ErrorResponse) r.getBody()).errorCode())
                  .isEqualTo(ErrorCode.ADMIN_WRITE_DISABLED.name());
            })
        .verifyComplete();
  }

  @Test
  void nonPlatformAdmin_returns403() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    when(auth.adminWriteFeatureEnabled()).thenReturn(true);
    when(auth.enterpriseAdminWriteDenialReason(any()))
        .thenReturn(Optional.of(ErrorCode.ADMIN_ACCESS_DENIED));
    var controller =
        new AdminEnterpriseChangeRequestController(auth, mock(EnterpriseChangeRequestProxyService.class));

    StepVerifier.create(controller.list(jwtMember(), null, null))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(((ErrorResponse) r.getBody()).errorCode())
                  .isEqualTo(ErrorCode.ADMIN_ACCESS_DENIED.name());
            })
        .verifyComplete();
  }

  @Test
  void whenAllowed_proxiesList() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    when(auth.adminWriteFeatureEnabled()).thenReturn(true);
    when(auth.enterpriseAdminWriteDenialReason(any())).thenReturn(Optional.empty());
    EnterpriseChangeRequestProxyService proxy = mock(EnterpriseChangeRequestProxyService.class);
    when(proxy.list(eq(null), eq("sub-1"), eq("a@b.com"), eq("rid"), eq(AdminEnterpriseChangeRequestController.PATH_PREFIX)))
        .thenReturn(
            Mono.just(ResponseEntity.ok().body(Map.of("items", java.util.List.of(Map.of("id", UUID.randomUUID()))))));
    var controller = new AdminEnterpriseChangeRequestController(auth, proxy);

    StepVerifier.create(controller.list(jwtPlatformAdmin(), null, "rid"))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK))
        .verifyComplete();
  }

  @Test
  void approve_whenMfaRequired_returns403() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    when(auth.adminWriteFeatureEnabled()).thenReturn(true);
    when(auth.enterpriseAdminWriteDenialReason(any()))
        .thenReturn(Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED));
    var controller =
        new AdminEnterpriseChangeRequestController(auth, mock(EnterpriseChangeRequestProxyService.class));
    UUID id = UUID.randomUUID();

    StepVerifier.create(controller.approve(jwtPlatformAdmin(), id, Map.of("reason", "ok"), null))
        .assertNext(
            r -> {
              assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(((ErrorResponse) r.getBody()).errorCode())
                  .isEqualTo(ErrorCode.ADMIN_WRITE_MFA_REQUIRED.name());
            })
        .verifyComplete();
  }

  @Test
  void whenAllowed_proxiesApprove() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    when(auth.adminWriteFeatureEnabled()).thenReturn(true);
    when(auth.enterpriseAdminWriteDenialReason(any())).thenReturn(Optional.empty());
    EnterpriseChangeRequestProxyService proxy = mock(EnterpriseChangeRequestProxyService.class);
    UUID id = UUID.randomUUID();
    when(proxy.approve(
            eq(id),
            eq(Map.of("reason", "reviewed")),
            eq("sub-1"),
            eq("a@b.com"),
            eq("rid"),
            eq(AdminEnterpriseChangeRequestController.PATH_PREFIX + "/" + id + "/approve")))
        .thenReturn(Mono.just(ResponseEntity.ok().body(Map.of("id", id.toString(), "status", "APPROVED"))));
    var controller = new AdminEnterpriseChangeRequestController(auth, proxy);

    StepVerifier.create(controller.approve(jwtPlatformAdmin(), id, Map.of("reason", "reviewed"), "rid"))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK))
        .verifyComplete();
  }

  private static Jwt jwtPlatformAdmin() {
    return Jwt.withTokenValue("t")
        .headers(h -> h.put("alg", "none"))
        .subject("sub-1")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("email", "a@b.com")
        .claim("token_type", "access")
        .claim("platform_roles", java.util.List.of("PLATFORM_ADMIN"))
        .claim("mfa_verified", true)
        .build();
  }

  private static Jwt jwtMember() {
    return Jwt.withTokenValue("t")
        .headers(h -> h.put("alg", "none"))
        .subject("sub-2")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("email", "m@b.com")
        .claim("token_type", "access")
        .claim("roles", java.util.List.of("ROLE_USER"))
        .build();
  }
}
