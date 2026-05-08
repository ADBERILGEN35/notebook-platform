package com.notebook.lumen.notification.user.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationSseEventEnvelope(
    UUID eventId,
    String originInstanceId,
    UUID recipientUserId,
    String eventType,
    Map<String, Object> payload,
    Instant createdAt) {}
