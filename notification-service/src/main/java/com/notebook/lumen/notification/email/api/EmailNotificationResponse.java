package com.notebook.lumen.notification.email.api;

import com.notebook.lumen.notification.email.domain.EmailNotificationStatus;
import java.util.UUID;

public record EmailNotificationResponse(UUID notificationId, EmailNotificationStatus status) {}
