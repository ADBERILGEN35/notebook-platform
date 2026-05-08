package com.notebook.lumen.identity.notification;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface NotificationClient {
  @PostExchange("/internal/notifications/email")
  NotificationResponse sendEmail(@RequestBody NotificationEmailRequest request);

  @PostExchange("/internal/notifications/in-app")
  NotificationResponse sendInApp(@RequestBody InAppNotificationRequest request);

  record NotificationEmailRequest(
      EmailNotificationType type,
      String recipientEmail,
      String subject,
      String templateKey,
      Map<String, String> templateVariables,
      String idempotencyKey) {}

  record InAppNotificationRequest(
      UUID recipientUserId,
      UUID workspaceId,
      InAppNotificationType type,
      String title,
      String message,
      String severity,
      String actionUrl,
      Map<String, Object> metadata,
      String idempotencyKey) {}

  record NotificationResponse(UUID notificationId, String status) {}

  enum EmailNotificationType {
    SECURITY_REFRESH_TOKENS_REVOKED
  }

  enum InAppNotificationType {
    SECURITY_SESSIONS_REVOKED
  }
}
