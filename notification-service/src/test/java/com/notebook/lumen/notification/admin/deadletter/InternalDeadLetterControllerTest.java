package com.notebook.lumen.notification.admin.deadletter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class InternalDeadLetterControllerTest {

  @Test
  void whenDisabled_throws404() {
    var c =
        new InternalDeadLetterController(
            new InternalDeadLetterAdminProperties(false),
            mock(InternalNotificationAuthorizer.class),
            mock(FanoutDeadLetterAdminService.class));
    assertThatThrownBy(() -> c.list(null, "fanout", "DEAD", null, null, null, 0, 50, null))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void whenEnabled_listAuthorizesRead() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    FanoutDeadLetterAdminService svc = mock(FanoutDeadLetterAdminService.class);
    Mockito.when(svc.list(null, null, null, 0, 10, null))
        .thenReturn(new DeadLetterAdminDtos.DeadLetterListResponse(List.of(), 0, 10, 0));
    var c =
        new InternalDeadLetterController(new InternalDeadLetterAdminProperties(true), auth, svc);
    c.list("Bearer t", "fanout", "DEAD", null, null, null, 0, 10, null);
    verify(auth)
        .authorize(
            "Bearer t", InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_DEAD_LETTER_READ_SCOPE);
    verify(svc).list(null, null, null, 0, 10, null);
  }

  @Test
  void whenEnabled_requeueAuthorizesRequeueScope() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    FanoutDeadLetterAdminService svc = mock(FanoutDeadLetterAdminService.class);
    UUID id = UUID.randomUUID();
    var c =
        new InternalDeadLetterController(new InternalDeadLetterAdminProperties(true), auth, svc);
    c.requeue(
        "Bearer t",
        "actor",
        id,
        new DeadLetterAdminDtos.FanoutRequeueHttpRequest("k", "reason reason reason"));
    verify(auth)
        .authorize(
            "Bearer t",
            InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_DEAD_LETTER_REQUEUE_SCOPE);
    verify(svc).requeue(eq(id), eq("k"), eq("reason reason reason"), eq("actor"));
  }
}
