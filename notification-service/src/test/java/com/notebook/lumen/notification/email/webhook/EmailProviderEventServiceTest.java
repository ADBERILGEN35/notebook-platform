package com.notebook.lumen.notification.email.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.email.domain.EmailDeliveryStatus;
import com.notebook.lumen.notification.email.domain.EmailNotification;
import com.notebook.lumen.notification.email.domain.EmailNotificationType;
import com.notebook.lumen.notification.email.infrastructure.EmailNotificationRepository;
import com.notebook.lumen.notification.email.suppression.EmailSuppressionReason;
import com.notebook.lumen.notification.email.suppression.EmailSuppressionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailProviderEventServiceTest {
  private final EmailProviderEventRepository eventRepository =
      mock(EmailProviderEventRepository.class);
  private final EmailNotificationRepository notificationRepository =
      mock(EmailNotificationRepository.class);
  private final EmailSuppressionService suppressionService = mock(EmailSuppressionService.class);
  private final EmailProviderEventService service =
      new EmailProviderEventService(
          eventRepository,
          notificationRepository,
          suppressionService,
          mock(AuditService.class),
          new SimpleMeterRegistry());

  @Test
  void deliveredUpdatesNotification() {
    EmailNotification notification = sentNotification();
    when(eventRepository.findByProviderAndProviderEventId("generic-http", "evt-1"))
        .thenReturn(Optional.empty());
    when(notificationRepository.findByProviderAndProviderMessageId("generic-http", "msg-1"))
        .thenReturn(Optional.of(notification));

    service.process(
        "generic-http",
        new EmailProviderWebhookEvent(
            "evt-1",
            "msg-1",
            EmailProviderEventType.DELIVERED,
            "user@example.com",
            Instant.now(),
            "{}"));

    assertThat(notification.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.DELIVERED);
    verify(suppressionService, never()).createIfAbsent(any(), any(), any(), any(), any());
  }

  @Test
  void bounceCreatesSuppression() {
    EmailNotification notification = sentNotification();
    when(eventRepository.findByProviderAndProviderEventId("generic-http", "evt-2"))
        .thenReturn(Optional.empty());
    when(notificationRepository.findByProviderAndProviderMessageId("generic-http", "msg-1"))
        .thenReturn(Optional.of(notification));

    service.process(
        "generic-http",
        new EmailProviderWebhookEvent(
            "evt-2",
            "msg-1",
            EmailProviderEventType.BOUNCE,
            "user@example.com",
            Instant.now(),
            "{}"));

    assertThat(notification.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.BOUNCED);
    verify(suppressionService)
        .createIfAbsent(
            "user@example.com", EmailSuppressionReason.BOUNCE, "generic-http", "evt-2", "WEBHOOK");
  }

  @Test
  void duplicateEventIsIdempotent() {
    when(eventRepository.findByProviderAndProviderEventId("generic-http", "evt-1"))
        .thenReturn(Optional.of(mock(EmailProviderEvent.class)));

    service.process(
        "generic-http",
        new EmailProviderWebhookEvent(
            "evt-1",
            "msg-1",
            EmailProviderEventType.COMPLAINT,
            "user@example.com",
            Instant.now(),
            "{}"));

    verify(notificationRepository, never()).findByProviderAndProviderMessageId(any(), any());
  }

  private EmailNotification sentNotification() {
    EmailNotification notification =
        new EmailNotification(
            UUID.randomUUID(),
            EmailNotificationType.WORKSPACE_INVITATION,
            "user@example.com",
            "Subject",
            "text",
            "html",
            "idempotency",
            Instant.now());
    notification.markSent("generic-http", "msg-1", Instant.now());
    return notification;
  }
}
