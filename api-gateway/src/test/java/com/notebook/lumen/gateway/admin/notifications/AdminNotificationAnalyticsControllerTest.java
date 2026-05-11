package com.notebook.lumen.gateway.admin.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AdminNotificationAnalyticsControllerTest {

  @Mock private AdminAuthorizationService adminAuthorizationService;
  @Mock private AdminNotificationAnalyticsProxyService proxyService;

  @Test
  void enterpriseDisabled_returns404() {
    when(adminAuthorizationService.enterpriseFeatureEnabled()).thenReturn(false);
    var c = new AdminNotificationAnalyticsController(adminAuthorizationService, proxyService);
    StepVerifier.create(
            c.summary(mock(Jwt.class), Instant.now(), Instant.now().plusSeconds(60), null, "r1"))
        .assertNext(
            resp -> {
              assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(resp.getBody()).isInstanceOf(ErrorResponse.class);
            })
        .verifyComplete();
    verifyNoInteractions(proxyService);
  }

  @Test
  void permissionDenied_returns403() {
    when(adminAuthorizationService.enterpriseFeatureEnabled()).thenReturn(true);
    when(adminAuthorizationService.ensureAdminPermission(
            any(), eq(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_ANALYTICS_READ)))
        .thenReturn(Optional.of(ErrorCode.ADMIN_PERMISSION_REQUIRED));
    var c = new AdminNotificationAnalyticsController(adminAuthorizationService, proxyService);
    Jwt jwt = mock(Jwt.class);
    StepVerifier.create(c.summary(jwt, Instant.now(), Instant.now().plusSeconds(60), null, "r1"))
        .assertNext(resp -> assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
        .verifyComplete();
    verifyNoInteractions(proxyService);
  }

  @Test
  void ok_proxiesSummary() throws Exception {
    when(adminAuthorizationService.enterpriseFeatureEnabled()).thenReturn(true);
    when(adminAuthorizationService.ensureAdminPermission(
            any(), eq(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_ANALYTICS_READ)))
        .thenReturn(Optional.empty());
    JsonNode body = new ObjectMapper().createObjectNode().put("bucket", "hour");
    when(proxyService.fetchSummary(any(), any(), any())).thenReturn(Mono.just(body));
    var c = new AdminNotificationAnalyticsController(adminAuthorizationService, proxyService);
    StepVerifier.create(
            c.summary(mock(Jwt.class), Instant.now(), Instant.now().plusSeconds(60), null, "r1"))
        .assertNext(
            resp -> {
              assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(resp.getBody()).isInstanceOf(JsonNode.class);
              assertThat(((JsonNode) resp.getBody()).get("bucket").asText()).isEqualTo("hour");
            })
        .verifyComplete();
  }
}
