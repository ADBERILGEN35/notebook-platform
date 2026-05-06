package com.notebook.lumen.workspace.client;

import com.notebook.lumen.workspace.domain.Invitation;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface NotificationClient {
  @PostExchange("/internal/notifications/email")
  NotificationResponse sendEmail(@RequestBody NotificationEmailRequest request);

  record NotificationEmailRequest(
      EmailNotificationType type,
      String recipientEmail,
      String subject,
      String templateKey,
      Map<String, String> templateVariables,
      String idempotencyKey) {}

  record NotificationResponse(UUID notificationId, String status) {}

  enum EmailNotificationType {
    WORKSPACE_INVITATION,
    SECURITY_REFRESH_TOKENS_REVOKED,
    SECURITY_LOGIN_NEW_DEVICE,
    SECURITY_PASSWORD_CHANGED,
    GENERIC_SECURITY_NOTICE
  }

  static NotificationEmailRequest workspaceInvitation(
      Invitation invitation, String inviterEmail, String acceptUrl) {
    return new NotificationEmailRequest(
        EmailNotificationType.WORKSPACE_INVITATION,
        invitation.getEmail(),
        "Workspace invitation",
        "workspace-invitation",
        Map.of(
            "workspaceName",
            invitation.getWorkspaceId().toString(),
            "inviterEmail",
            inviterEmail,
            "role",
            invitation.getRole().name(),
            "acceptUrl",
            acceptUrl),
        "workspace-invitation:" + invitation.getId());
  }
}
