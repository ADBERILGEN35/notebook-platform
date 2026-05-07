package com.notebook.lumen.identity.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecurityNotificationServiceTest {
  private final IdentityNotificationProperties properties =
      new IdentityNotificationProperties(true, "http://localhost", 1000, null);
  private final NotificationClient notificationClient = mock(NotificationClient.class);
  private final AuditService auditService = mock(AuditService.class);
  private final SecurityNotificationService service =
      new SecurityNotificationService(properties, notificationClient, auditService);

  @Test
  void revokeAllNotificationRequestUsesSecurityTemplate() {
    User user = user();

    service.refreshTokensRevoked(user, 2, mock(HttpServletRequest.class));

    verify(notificationClient)
        .sendEmail(
            org.mockito.ArgumentMatchers.argThat(
                request ->
                    request.type()
                            == NotificationClient.EmailNotificationType
                                .SECURITY_REFRESH_TOKENS_REVOKED
                        && request.recipientEmail().equals("user@example.com")
                        && request.templateKey().equals("security-refresh-tokens-revoked")
                        && request.idempotencyKey().startsWith("security-revoke-all:")));
  }

  @Test
  void notificationFailureDoesNotThrow() {
    when(notificationClient.sendEmail(any())).thenThrow(new IllegalStateException("down"));

    service.refreshTokensRevoked(user(), 1, mock(HttpServletRequest.class));

    verify(auditService)
        .record(
            org.mockito.ArgumentMatchers.eq("SECURITY_NOTIFICATION_FAILED"),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  private User user() {
    Instant now = Instant.now();
    return new User(
        UUID.randomUUID(),
        "user@example.com",
        "User",
        null,
        "hash",
        UserStatus.ACTIVE,
        null,
        null,
        now,
        now,
        null,
        null);
  }
}
