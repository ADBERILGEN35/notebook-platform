package com.notebook.lumen.gateway.admin.enterprise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.test.StepVerifier;

class AdminEnterpriseStatusControllerDirectTest {

  @Test
  void enterpriseDisabled_yields404Body() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    when(auth.enterpriseFeatureEnabled()).thenReturn(false);
    var controller =
        new AdminEnterpriseStatusController(auth, mock(EnterpriseStatusAggregationService.class));

    StepVerifier.create(controller.enterpriseStatus(jwt(), null))
        .assertNext(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
              assertThat(((ErrorResponse) response.getBody()).errorCode())
                  .isEqualTo(ErrorCode.ADMIN_ENTERPRISE_DISABLED.name());
            })
        .verifyComplete();
  }

  @Test
  void whenAdmin_loadsAggregation() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    when(auth.enterpriseFeatureEnabled()).thenReturn(true);
    when(auth.isAdmin(any())).thenReturn(true);
    EnterpriseStatusAggregationService agg = mock(EnterpriseStatusAggregationService.class);
    var payload =
        new EnterpriseStatusResponse(
            "test",
            Instant.parse("2026-01-01T00:00:00Z"),
            new EnterpriseStatusFeatures(
                new SsoStatus(false, 0, false, false, false),
                new ScimStatus(false, false, false, false),
                new MfaStatus("off", List.of(), false, false),
                new SiemStatus(false, "noop", false, false, false),
                new AuditExportStatus(false, false, false, false, "", false),
                new NotificationsStatus(false, false, false, false),
                new GatewaySecurityStatus(
                    true, true, "off", List.of(), true, false, "bearer", false),
                new MergeResolutionStatus(false, false, List.of(1), true, true, false)),
            List.of(),
            false,
            false,
            false);
    when(agg.loadStatus()).thenReturn(reactor.core.publisher.Mono.just(payload));
    var controller = new AdminEnterpriseStatusController(auth, agg);

    StepVerifier.create(controller.enterpriseStatus(jwt(), null))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK))
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
