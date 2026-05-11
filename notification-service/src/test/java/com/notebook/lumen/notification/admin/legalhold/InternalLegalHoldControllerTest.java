package com.notebook.lumen.notification.admin.legalhold;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class InternalLegalHoldControllerTest {

  @Test
  void whenDisabled_listThrows404() {
    var c =
        new InternalLegalHoldController(
            new InternalLegalHoldAdminProperties(false),
            mock(InternalNotificationAuthorizer.class),
            mock(NotificationLegalHoldAdminService.class));
    assertThatThrownBy(() -> c.list(null, null)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void listAuthorizesRead() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    NotificationLegalHoldAdminService svc = mock(NotificationLegalHoldAdminService.class);
    Mockito.when(svc.list(java.util.Optional.empty()))
        .thenReturn(new LegalHoldAdminDtos.LegalHoldListResponse(List.of()));
    var c = new InternalLegalHoldController(new InternalLegalHoldAdminProperties(true), auth, svc);
    c.list("Bearer t", null);
    verify(auth)
        .authorize(
            "Bearer t", InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_LEGAL_HOLD_READ_SCOPE);
  }

  @Test
  void createAuthorizesWrite() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    NotificationLegalHoldAdminService svc = mock(NotificationLegalHoldAdminService.class);
    UUID actor = UUID.randomUUID();
    Mockito.when(svc.create(Mockito.any(), Mockito.eq(actor), Mockito.any()))
        .thenReturn(
            new LegalHoldAdminDtos.LegalHoldResponse(
                UUID.randomUUID(),
                "k",
                "FANOUT_OUTBOX",
                "ACTIVE",
                java.time.Instant.parse("2026-05-10T00:00:00Z"),
                null,
                actor,
                null,
                null,
                null));
    var c = new InternalLegalHoldController(new InternalLegalHoldAdminProperties(true), auth, svc);
    c.create(
        "Bearer t",
        actor.toString(),
        null,
        new LegalHoldAdminDtos.LegalHoldCreateRequest("k", "FANOUT_OUTBOX", "12345678901", null));
    verify(auth)
        .authorize(
            "Bearer t", InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_LEGAL_HOLD_WRITE_SCOPE);
  }
}
