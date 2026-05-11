package com.notebook.lumen.notification.user.api;

import java.util.UUID;

public record InternalInAppNotificationResponse(
    UUID notificationId, String status, String skippedReason) {}
