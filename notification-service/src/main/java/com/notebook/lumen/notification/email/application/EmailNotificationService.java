package com.notebook.lumen.notification.email.application;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.email.api.EmailNotificationRequest;
import com.notebook.lumen.notification.email.api.EmailNotificationResponse;
import com.notebook.lumen.notification.email.domain.EmailNotification;
import com.notebook.lumen.notification.email.domain.EmailNotificationStatus;
import com.notebook.lumen.notification.email.infrastructure.EmailNotificationRepository;
import com.notebook.lumen.notification.email.provider.EmailMessage;
import com.notebook.lumen.notification.email.provider.EmailProvider;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.template.application.EmailTemplateRenderer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailNotificationService {
  private final EmailNotificationRepository repository;
  private final EmailTemplateRenderer templateRenderer;
  private final EmailProvider provider;
  private final NotificationProperties properties;
  private final AuditService auditService;

  public EmailNotificationService(
      EmailNotificationRepository repository,
      EmailTemplateRenderer templateRenderer,
      EmailProvider provider,
      NotificationProperties properties,
      AuditService auditService) {
    this.repository = repository;
    this.templateRenderer = templateRenderer;
    this.provider = provider;
    this.properties = properties;
    this.auditService = auditService;
  }

  @Transactional
  public EmailNotificationResponse enqueue(EmailNotificationRequest request) {
    if (hasText(request.idempotencyKey())) {
      var existing = repository.findByIdempotencyKey(request.idempotencyKey());
      if (existing.isPresent()) {
        EmailNotification notification = existing.get();
        return new EmailNotificationResponse(notification.getId(), notification.getStatus());
      }
    }
    var rendered =
        templateRenderer.render(
            request.templateKey(),
            request.templateVariables() == null ? Map.of() : request.templateVariables());
    Instant now = Instant.now();
    EmailNotification notification =
        new EmailNotification(
            UUID.randomUUID(),
            request.type(),
            normalizeEmail(request.recipientEmail()),
            request.subject(),
            rendered.bodyText(),
            rendered.bodyHtml(),
            blankToNull(request.idempotencyKey()),
            now);
    repository.save(notification);
    auditService.record(
        "EMAIL_NOTIFICATION_QUEUED",
        "EMAIL_NOTIFICATION",
        notification.getId(),
        Map.of(
            "notificationId",
            notification.getId().toString(),
            "type",
            notification.getType().name(),
            "recipientEmailMasked",
            maskEmail(notification.getRecipientEmail())));
    return new EmailNotificationResponse(notification.getId(), notification.getStatus());
  }

  @Transactional
  public void processDueNotifications() {
    if (!properties.email().workerEnabled()) {
      return;
    }
    Instant now = Instant.now();
    var notifications =
        repository.findDueNotifications(EmailNotificationStatus.PENDING.name(), now);
    for (EmailNotification notification : notifications) {
      send(notification, now);
    }
  }

  private void send(EmailNotification notification, Instant now) {
    notification.markSending(now);
    try {
      var result =
          provider.send(
              new EmailMessage(
                  notification.getRecipientEmail(),
                  notification.getSubject(),
                  notification.getBodyText(),
                  notification.getBodyHtml(),
                  Map.of(
                      "notificationId",
                      notification.getId().toString(),
                      "type",
                      notification.getType().name())));
      notification.markSent(result.provider(), result.providerMessageId(), result.acceptedAt());
      auditService.record(
          "EMAIL_NOTIFICATION_SENT",
          "EMAIL_NOTIFICATION",
          notification.getId(),
          Map.of(
              "notificationId",
              notification.getId().toString(),
              "type",
              notification.getType().name(),
              "provider",
              result.provider(),
              "attemptCount",
              notification.getAttemptCount()));
    } catch (RuntimeException e) {
      handleFailure(notification, e);
    }
  }

  private void handleFailure(EmailNotification notification, RuntimeException e) {
    Instant now = Instant.now();
    int nextAttempt = notification.getAttemptCount() + 1;
    if (nextAttempt >= properties.email().maxAttempts()) {
      notification.markFailed(safeError(e), now);
      auditService.record(
          "EMAIL_NOTIFICATION_FAILED",
          "EMAIL_NOTIFICATION",
          notification.getId(),
          Map.of(
              "notificationId",
              notification.getId().toString(),
              "type",
              notification.getType().name(),
              "attemptCount",
              nextAttempt,
              "error",
              e.getClass().getSimpleName()));
      return;
    }
    notification.markRetry(safeError(e), nextAttemptAt(nextAttempt, now), now);
  }

  private Instant nextAttemptAt(int nextAttempt, Instant now) {
    long initialDelay = Math.max(1, properties.email().retryInitialDelaySeconds());
    long maxDelay = Math.max(initialDelay, properties.email().retryMaxDelaySeconds());
    long multiplier = 1L << Math.min(nextAttempt - 1, 10);
    long delay = Math.min(maxDelay, initialDelay * multiplier);
    return now.plusSeconds(delay);
  }

  private String safeError(RuntimeException e) {
    String message = e.getMessage();
    if (!hasText(message)) {
      return e.getClass().getSimpleName();
    }
    return e.getClass().getSimpleName() + ": " + message;
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private String blankToNull(String value) {
    return hasText(value) ? value : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String maskEmail(String email) {
    int at = email.indexOf('@');
    if (at <= 1) {
      return "****";
    }
    return email.charAt(0) + "****" + email.substring(at);
  }
}
