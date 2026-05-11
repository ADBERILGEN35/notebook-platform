package com.notebook.lumen.notification.admin.retention;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class InternalRetentionControllerTest {

  @Test
  void whenDisabled_planThrows404() {
    var c =
        new InternalRetentionController(
            new InternalRetentionAdminProperties(false),
            mock(InternalNotificationAuthorizer.class),
            mock(NotificationRetentionAdminService.class));
    assertThatThrownBy(() -> c.plan(null, true)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void planAuthorizesRead() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    NotificationRetentionAdminService svc = mock(NotificationRetentionAdminService.class);
    var plan =
        new RetentionAdminDtos.RetentionPlanResponse(
            Instant.parse("2026-05-10T00:00:00Z"), true, List.of(), List.of());
    Mockito.when(svc.plan(Mockito.any(), eq(true))).thenReturn(plan);
    var c = new InternalRetentionController(new InternalRetentionAdminProperties(true), auth, svc);
    c.plan("Bearer t", true);
    verify(auth)
        .authorize(
            "Bearer t", InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_RETENTION_READ_SCOPE);
    verify(svc).plan(Mockito.any(), eq(true));
  }

  @Test
  void runDryAuthorizesRead() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    NotificationRetentionAdminService svc = mock(NotificationRetentionAdminService.class);
    var snap =
        new RetentionAdminDtos.RetentionPlanResponse(
            Instant.parse("2026-05-10T00:00:00Z"), true, List.of(), List.of());
    Mockito.when(svc.run(true, "ALL", null, null))
        .thenReturn(
            new RetentionAdminDtos.RetentionRunResponse(
                true, "ALL", 0, java.util.Map.of(), 0L, java.util.List.of(), snap));
    var c = new InternalRetentionController(new InternalRetentionAdminProperties(true), auth, svc);
    c.run("Bearer t", null, new RetentionAdminDtos.RetentionRunRequest(true, "ALL", null));
    verify(auth)
        .authorize(
            "Bearer t", InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_RETENTION_READ_SCOPE);
  }

  @Test
  void runPurgeAuthorizesRun() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    NotificationRetentionAdminService svc = mock(NotificationRetentionAdminService.class);
    var snap =
        new RetentionAdminDtos.RetentionPlanResponse(
            Instant.parse("2026-05-10T00:00:00Z"), false, List.of(), List.of());
    Mockito.when(svc.run(false, "ALL", "reason reason reason", "actor"))
        .thenReturn(
            new RetentionAdminDtos.RetentionRunResponse(
                false, "ALL", 1, java.util.Map.of(), 0L, java.util.List.of(), snap));
    var c = new InternalRetentionController(new InternalRetentionAdminProperties(true), auth, svc);
    c.run(
        "Bearer t",
        "actor",
        new RetentionAdminDtos.RetentionRunRequest(false, "ALL", "reason reason reason"));
    verify(auth)
        .authorize(
            "Bearer t", InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_RETENTION_RUN_SCOPE);
  }
}
