package com.notebook.lumen.notification.preference.api;

import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceUpdateItem(
    @NotNull UserNotificationType notificationType,
    @NotNull NotificationChannel channel,
    @NotNull Boolean enabled) {}
