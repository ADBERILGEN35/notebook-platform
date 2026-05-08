package com.notebook.lumen.notification.user.api;

import com.notebook.lumen.notification.user.domain.UserNotification;
import com.notebook.lumen.notification.user.domain.UserNotificationSeverity;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.time.Instant;
import java.util.UUID;

public record UserNotificationResponse(
    UUID id,
    UUID workspaceId,
    UserNotificationType type,
    String title,
    String message,
    UserNotificationSeverity severity,
    String actionUrl,
    boolean unread,
    Instant readAt,
    Instant createdAt) {
  public static UserNotificationResponse from(UserNotification notification) {
    return new UserNotificationResponse(
        notification.getId(),
        notification.getWorkspaceId(),
        notification.getType(),
        notification.getTitle(),
        notification.getMessage(),
        notification.getSeverity(),
        notification.getActionUrl(),
        notification.getReadAt() == null,
        notification.getReadAt(),
        notification.getCreatedAt());
  }
}
