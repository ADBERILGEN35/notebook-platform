package com.notebook.lumen.notification.user.api;

import com.notebook.lumen.notification.user.domain.UserNotificationSeverity;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record InAppNotificationCreateRequest(
    @NotNull UUID recipientUserId,
    UUID workspaceId,
    @NotNull UserNotificationType type,
    @NotBlank String title,
    @NotBlank String message,
    @NotNull UserNotificationSeverity severity,
    String actionUrl,
    Map<String, Object> metadata,
    String idempotencyKey) {}
