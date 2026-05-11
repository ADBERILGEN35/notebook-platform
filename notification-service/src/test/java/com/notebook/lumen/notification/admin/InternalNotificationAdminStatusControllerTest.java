package com.notebook.lumen.notification.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalNotificationAdminStatusControllerTest {

  @Test
  void whenDisabled_throws404() {
    var c =
        new InternalNotificationAdminStatusController(
            new InternalAdminStatusProperties(false),
            mock(InternalNotificationAuthorizer.class),
            mock(NotificationAdminStatusService.class));
    assertThatThrownBy(() -> c.notification("Bearer x"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void whenEnabled_authorizesWithScope() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    NotificationAdminStatusService svc = mock(NotificationAdminStatusService.class);
    var c =
        new InternalNotificationAdminStatusController(
            new InternalAdminStatusProperties(true), auth, svc);
    c.notification("Bearer t");
    verify(auth).authorize("Bearer t", InternalNotificationAuthorizer.ADMIN_STATUS_SCOPE);
  }
}
