package com.notebook.lumen.notification.preference.api;

import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.Map;

public record NotificationPreferenceResponse(
    UserNotificationType notificationType,
    String label,
    String description,
    Map<NotificationChannel, NotificationPreferenceChannelState> channels) {}
