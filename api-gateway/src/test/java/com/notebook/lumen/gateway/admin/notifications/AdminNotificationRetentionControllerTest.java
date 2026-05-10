package com.notebook.lumen.gateway.admin.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AdminNotificationRetentionControllerTest {

  @Test
  void planDeniedWithoutPermission() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    when(auth.enterpriseFeatureEnabled()).thenReturn(true);
    when(auth.ensureNotificationRetentionRead(any()))
        .thenReturn(Optional.of(ErrorCode.ADMIN_PERMISSION_REQUIRED));
    var c = new AdminNotificationRetentionController(auth, mock(AdminNotificationRetentionProxyService.class));
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u1").build();
    StepVerifier.create(c.plan(jwt, true, null))
        .expectNextMatches(resp -> resp.getStatusCode().value() == 403)
        .verifyComplete();
  }

  @Test
  void runDryUsesReadPermission() {
    AdminAuthorizationService auth = mock(AdminAuthorizationService.class);
    AdminNotificationRetentionProxyService proxy = mock(AdminNotificationRetentionProxyService.class);
    when(auth.enterpriseFeatureEnabled()).thenReturn(true);
    when(auth.ensureNotificationRetentionRead(any())).thenReturn(Optional.empty());
    ObjectNode body = new ObjectMapper().createObjectNode().put("dryRun", true).put("target", "ALL");
    when(proxy.run(any(), eq(false), eq("u1"))).thenReturn(Mono.just(body));
    var c = new AdminNotificationRetentionController(auth, proxy);
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u1").build();
    StepVerifier.create(c.run(jwt, Map.of("dryRun", true, "target", "ALL"), null))
        .expectNextMatches(resp -> resp.getStatusCode().is2xxSuccessful())
        .verifyComplete();
  }
}
