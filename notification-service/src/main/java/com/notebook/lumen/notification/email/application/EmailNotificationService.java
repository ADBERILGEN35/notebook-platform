package com.notebook.lumen.notification.email.application;

import com.notebook.lumen.common.security.worker.WorkerInstanceIds;
import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.email.api.EmailNotificationRequest;
import com.notebook.lumen.notification.email.api.EmailNotificationResponse;
import com.notebook.lumen.notification.email.domain.EmailNotification;
import com.notebook.lumen.notification.email.domain.EmailNotificationStatus;
import com.notebook.lumen.notification.email.infrastructure.EmailNotificationRepository;
import com.notebook.lumen.notification.email.provider.EmailMessage;
import com.notebook.lumen.notification.email.provider.EmailProvider;
import com.notebook.lumen.notification.email.suppression.EmailSuppressionService;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.template.application.EmailTemplateRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailNotificationService {
  private final EmailNotificationRepository repository;
  private final EmailTemplateRenderer templateRenderer;
  private final EmailProvider provider;
  private final EmailSuppressionService suppressionService;
  private final NotificationProperties properties;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;
  private final String workerInstanceId;

  public EmailNotificationService(
      EmailNotificationRepository repository,
      EmailTemplateRenderer templateRenderer,
      EmailProvider provider,
      EmailSuppressionService suppressionService,
      NotificationProperties properties,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.templateRenderer = templateRenderer;
    this.provider = provider;
    this.suppressionService = suppressionService;
    this.properties = properties;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
    this.workerInstanceId =
        WorkerInstanceIds.resolve(properties.workerInstanceId(), "email-worker");
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
    String recipientEmail = normalizeEmail(request.recipientEmail());
    if (suppressionService.active(recipientEmail).isPresent()) {
      auditService.record(
          "EMAIL_SUPPRESSED",
          "EMAIL_NOTIFICATION",
          UUID.nameUUIDFromBytes(recipientEmail.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
          Map.of("recipientEmailMasked", maskEmail(recipientEmail), "source", "enqueue"));
      throw new NotificationException(
          HttpStatus.CONFLICT,
          "EMAIL_RECIPIENT_SUPPRESSED",
          "Email recipient is suppressed");
    }
    EmailNotification notification =
        new EmailNotification(
            UUID.randomUUID(),
            request.type(),
            recipientEmail,
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
    recoverExpiredSending(now);
    var notifications =
        repository.findDueNotifications(
            EmailNotificationStatus.PENDING.name(), now, effectiveBatchSize());
    if (!notifications.isEmpty()) {
      auditClaimed(notifications.size());
    }
    for (EmailNotification notification : notifications) {
      send(notification, now);
    }
  }

  private void send(EmailNotification notification, Instant now) {
    if (suppressionService.active(notification.getRecipientEmail()).isPresent()) {
      notification.markSuppressed(provider.providerName(), null, now, null);
      auditService.record(
          "EMAIL_SUPPRESSED",
          "EMAIL_NOTIFICATION",
          notification.getId(),
          Map.of(
              "notificationId",
              notification.getId().toString(),
              "type",
              notification.getType().name(),
              "recipientEmailMasked",
              maskEmail(notification.getRecipientEmail())));
      meterRegistry.counter("email_suppressed_total", "reason", "existing").increment();
      return;
    }
    notification.markSending(workerInstanceId, now, now.plusSeconds(effectiveLockTimeoutSeconds()));
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
      meterRegistry
          .counter(
              "email_provider_send_total",
              "provider",
              result.provider(),
              "status",
              safeValue(result.providerStatus()))
          .increment();
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
              notification.getAttemptCount(),
              "workerInstanceId",
              workerInstanceId));
    } catch (RuntimeException e) {
      meterRegistry
          .counter("email_provider_send_failure_total", "provider", safeValue(provider.providerName()))
          .increment();
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

  private void recoverExpiredSending(Instant now) {
    var expired =
        repository.findExpiredSendingForUpdate(
            EmailNotificationStatus.SENDING.name(), now, effectiveBatchSize());
    if (expired == null) {
      expired = java.util.List.of();
    }
    for (EmailNotification notification : expired) {
      String lockedBy = notification.getLockedBy();
      notification.recoverStaleSending("Sending lock expired", now);
      auditService.record(
          "EMAIL_NOTIFICATION_STALE_RECOVERED",
          "EMAIL_NOTIFICATION",
          notification.getId(),
          Map.of(
              "notificationId",
              notification.getId().toString(),
              "type",
              notification.getType().name(),
              "oldStatus",
              EmailNotificationStatus.SENDING.name(),
              "newStatus",
              EmailNotificationStatus.PENDING.name(),
              "lockedBy",
              safeValue(lockedBy),
              "workerInstanceId",
              workerInstanceId,
              "attemptCount",
              notification.getAttemptCount()));
    }
    if (!expired.isEmpty()) {
      meterRegistry.counter("email_worker_stale_recovered_total").increment(expired.size());
      meterRegistry.counter("email_worker_lock_expired_total").increment(expired.size());
    }
  }

  private void auditClaimed(int count) {
    meterRegistry.counter("email_worker_claimed_total").increment(count);
  }

  private int effectiveBatchSize() {
    return properties.email().workerBatchSize() <= 0 ? 25 : properties.email().workerBatchSize();
  }

  private long effectiveLockTimeoutSeconds() {
    return properties.email().workerLockTimeoutSeconds() <= 0
        ? 300
        : properties.email().workerLockTimeoutSeconds();
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

  private String safeValue(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }
}
