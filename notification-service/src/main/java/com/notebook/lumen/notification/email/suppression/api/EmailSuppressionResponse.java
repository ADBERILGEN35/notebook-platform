package com.notebook.lumen.notification.email.suppression.api;

import com.notebook.lumen.notification.email.suppression.EmailSuppression;
import com.notebook.lumen.notification.email.suppression.EmailSuppressionReason;
import java.time.Instant;
import java.util.UUID;

public record EmailSuppressionResponse(
    UUID id,
    String email,
    EmailSuppressionReason reason,
    String provider,
    String providerEventId,
    String source,
    Instant createdAt,
    Instant expiresAt,
    Instant releasedAt) {
  public static EmailSuppressionResponse from(EmailSuppression suppression) {
    return new EmailSuppressionResponse(
        suppression.getId(),
        suppression.getEmail(),
        suppression.getReason(),
        suppression.getProvider(),
        suppression.getProviderEventId(),
        suppression.getSource(),
        suppression.getCreatedAt(),
        suppression.getExpiresAt(),
        suppression.getReleasedAt());
  }
}
