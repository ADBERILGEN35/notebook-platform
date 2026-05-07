package com.notebook.lumen.notification.email.webhook;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.email.infrastructure.EmailNotificationRepository;
import com.notebook.lumen.notification.email.suppression.EmailSuppressionReason;
import com.notebook.lumen.notification.email.suppression.EmailSuppressionService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailProviderEventService {
  private final EmailProviderEventRepository eventRepository;
  private final EmailNotificationRepository notificationRepository;
  private final EmailSuppressionService suppressionService;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public EmailProviderEventService(
      EmailProviderEventRepository eventRepository,
      EmailNotificationRepository notificationRepository,
      EmailSuppressionService suppressionService,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.eventRepository = eventRepository;
    this.notificationRepository = notificationRepository;
    this.suppressionService = suppressionService;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public void process(String provider, EmailProviderWebhookEvent event) {
    if (eventRepository
        .findByProviderAndProviderEventId(provider, event.providerEventId())
        .isPresent()) {
      meterRegistry.counter("email_webhook_duplicate_total", "provider", provider).increment();
      return;
    }
    Instant now = Instant.now();
    eventRepository.save(
        new EmailProviderEvent(
            UUID.randomUUID(),
            provider,
            event.providerEventId(),
            event.providerMessageId(),
            event.eventType(),
            normalize(event.recipientEmail()),
            event.occurredAt(),
            event.sanitizedPayload(),
            now));
    meterRegistry
        .counter(
            "email_webhook_events_total",
            "provider",
            provider,
            "eventType",
            event.eventType().name().toLowerCase())
        .increment();
    auditService.record(
        "EMAIL_PROVIDER_WEBHOOK_RECEIVED",
        "EMAIL_PROVIDER_EVENT",
        UUID.nameUUIDFromBytes(
            (provider + event.providerEventId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        Map.of("provider", provider, "eventType", event.eventType().name()));
    if (event.eventType() == EmailProviderEventType.UNKNOWN || event.providerMessageId() == null) {
      return;
    }
    notificationRepository
        .findByProviderAndProviderMessageId(provider, event.providerMessageId())
        .ifPresent(
            notification -> {
              switch (event.eventType()) {
                case DELIVERED -> {
                  notification.markDelivered(
                      event.providerEventId(), event.occurredAt(), event.sanitizedPayload(), now);
                  meterRegistry
                      .counter(
                          "email_delivery_status_total",
                          "status",
                          "delivered",
                          "type",
                          notification.getType().name().toLowerCase(java.util.Locale.ROOT),
                          "provider",
                          provider)
                      .increment();
                  auditService.record(
                      "EMAIL_DELIVERED",
                      "EMAIL_NOTIFICATION",
                      notification.getId(),
                      Map.of("provider", provider));
                }
                case BOUNCE -> {
                  notification.markBounced(
                      event.providerEventId(), event.occurredAt(), event.sanitizedPayload(), now);
                  suppressionService.createIfAbsent(
                      notification.getRecipientEmail(),
                      EmailSuppressionReason.BOUNCE,
                      provider,
                      event.providerEventId(),
                      "WEBHOOK");
                  meterRegistry.counter("email_bounces_total", "provider", provider).increment();
                  meterRegistry
                      .counter(
                          "email_delivery_status_total",
                          "status",
                          "bounced",
                          "type",
                          notification.getType().name().toLowerCase(java.util.Locale.ROOT),
                          "provider",
                          provider)
                      .increment();
                  auditService.record(
                      "EMAIL_BOUNCED",
                      "EMAIL_NOTIFICATION",
                      notification.getId(),
                      Map.of("provider", provider));
                }
                case COMPLAINT -> {
                  notification.markComplained(
                      event.providerEventId(), event.occurredAt(), event.sanitizedPayload(), now);
                  suppressionService.createIfAbsent(
                      notification.getRecipientEmail(),
                      EmailSuppressionReason.COMPLAINT,
                      provider,
                      event.providerEventId(),
                      "WEBHOOK");
                  meterRegistry.counter("email_complaints_total", "provider", provider).increment();
                  meterRegistry
                      .counter(
                          "email_delivery_status_total",
                          "status",
                          "complained",
                          "type",
                          notification.getType().name().toLowerCase(java.util.Locale.ROOT),
                          "provider",
                          provider)
                      .increment();
                  auditService.record(
                      "EMAIL_COMPLAINED",
                      "EMAIL_NOTIFICATION",
                      notification.getId(),
                      Map.of("provider", provider));
                }
                case UNKNOWN -> {}
              }
            });
  }

  private String normalize(String email) {
    return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
