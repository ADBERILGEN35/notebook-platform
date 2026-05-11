package com.notebook.lumen.gateway.admin.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AdminNotificationDeadLetterControllerTest {

  @Mock private AdminAuthorizationService adminAuthorizationService;
  @Mock private AdminNotificationDeadLetterProxyService proxyService;

  @Test
  void list_enterpriseDisabled_returns404() {
    when(adminAuthorizationService.enterpriseFeatureEnabled()).thenReturn(false);
    var c = new AdminNotificationDeadLetterController(adminAuthorizationService, proxyService);
    StepVerifier.create(
            c.list(mock(Jwt.class), "fanout", "DEAD", null, null, null, 0, 50, null, "r1"))
        .assertNext(
            resp -> {
              assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(resp.getBody()).isInstanceOf(ErrorResponse.class);
            })
        .verifyComplete();
    verifyNoInteractions(proxyService);
  }

  @Test
  void requeue_missingMfa_returns403() {
    when(adminAuthorizationService.enterpriseFeatureEnabled()).thenReturn(true);
    when(adminAuthorizationService.ensureNotificationDeadLetterRequeue(any()))
        .thenReturn(Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED));
    var c = new AdminNotificationDeadLetterController(adminAuthorizationService, proxyService);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("a1");
    StepVerifier.create(
            c.requeue(
                jwt,
                UUID.randomUUID(),
                new AdminNotificationDeadLetterController.RequeueBody("k", "reason reason reason"),
                "r1"))
        .assertNext(resp -> assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
        .verifyComplete();
    verifyNoInteractions(proxyService);
  }

  @Test
  void requeue_ok_proxies() throws Exception {
    when(adminAuthorizationService.enterpriseFeatureEnabled()).thenReturn(true);
    when(adminAuthorizationService.ensureNotificationDeadLetterRequeue(any()))
        .thenReturn(Optional.empty());
    JsonNode body = new ObjectMapper().createObjectNode().put("status", "PENDING");
    when(proxyService.requeue(any(), eq("k"), eq("reason"), eq("subj")))
        .thenReturn(Mono.just(body));
    var c = new AdminNotificationDeadLetterController(adminAuthorizationService, proxyService);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("subj");
    UUID id = UUID.randomUUID();
    StepVerifier.create(
            c.requeue(
                jwt,
                id,
                new AdminNotificationDeadLetterController.RequeueBody("k", "reason"),
                "r1"))
        .assertNext(
            resp -> {
              assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(((JsonNode) resp.getBody()).get("status").asText()).isEqualTo("PENDING");
            })
        .verifyComplete();
  }
}
