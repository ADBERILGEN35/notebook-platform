package com.notebook.lumen.notification.email.webhook;

import java.time.Instant;

public record EmailProviderWebhookEvent(
    String providerEventId,
    String providerMessageId,
    EmailProviderEventType eventType,
    String recipientEmail,
    Instant occurredAt,
    String sanitizedPayload) {}
