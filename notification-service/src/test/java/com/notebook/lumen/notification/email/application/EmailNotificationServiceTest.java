package com.notebook.lumen.notification.email.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.email.api.EmailNotificationRequest;
import com.notebook.lumen.notification.email.domain.EmailNotification;
import com.notebook.lumen.notification.email.domain.EmailNotificationStatus;
import com.notebook.lumen.notification.email.domain.EmailNotificationType;
import com.notebook.lumen.notification.email.infrastructure.EmailNotificationRepository;
import com.notebook.lumen.notification.preference.application.NotificationDeliveryPreferenceService;
import com.notebook.lumen.notification.preference.application.NotificationPreferenceResolver;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.email.provider.EmailProvider;
import com.notebook.lumen.notification.email.provider.EmailProviderException;
import com.notebook.lumen.notification.email.provider.EmailSendResult;
import com.notebook.lumen.notification.email.suppression.EmailSuppressionService;
import com.notebook.lumen.notification.NotificationTestFanout;
import com.notebook.lumen.notification.NotificationTestWorkspace;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.template.application.EmailTemplateRenderer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailNotificationServiceTest {
  private final EmailNotificationRepository repository = mock(EmailNotificationRepository.class);
  private final EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
  private final EmailProvider provider = mock(EmailProvider.class);
  private final EmailSuppressionService suppressionService = mock(EmailSuppressionService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final NotificationPreferenceResolver preferenceResolver = mock(NotificationPreferenceResolver.class);
  private final NotificationDeliveryPreferenceService deliveryPreferenceService =
      mock(NotificationDeliveryPreferenceService.class);
  private final NotificationDigestService digestService = mock(NotificationDigestService.class);
  private final NotificationAnalyticsRecorder analyticsRecorder = mock(NotificationAnalyticsRecorder.class);

  {
    when(preferenceResolver.isChannelEnabled(any(), any(), any(), eq(NotificationChannel.EMAIL)))
        .thenReturn(true);
  }

  private final EmailNotificationService service =
      new EmailNotificationService(
          repository,
          renderer,
          provider,
          suppressionService,
          properties(),
          auditService,
          preferenceResolver,
          deliveryPreferenceService,
          digestService,
          new SimpleMeterRegistry(),
          analyticsRecorder);

  @Test
  void duplicateIdempotencyKeyReturnsExistingNotification() {
    EmailNotification existing =
        new EmailNotification(
            UUID.randomUUID(),
            EmailNotificationType.WORKSPACE_INVITATION,
            "user@example.com",
            "Subject",
            "text",
            "html",
            "workspace-invitation:1",
            Instant.now());
    when(repository.findByIdempotencyKey("workspace-invitation:1"))
        .thenReturn(Optional.of(existing));

    var response = service.enqueue(request("workspace-invitation:1"));

    assertThat(response.notificationId()).isEqualTo(existing.getId());
    assertThat(response.status()).isEqualTo(EmailNotificationStatus.PENDING);
  }

  @Test
  void providerSuccessMarksNotificationSent() {
    EmailNotification notification = notification();
    when(repository.findDueNotifications(
            eq(EmailNotificationStatus.PENDING.name()), any(), any(Integer.class)))
        .thenReturn(List.of(notification));
    when(provider.send(any()))
        .thenReturn(new EmailSendResult("noop", "provider-id", Instant.now(), "accepted", null));

    service.processDueNotifications();

    assertThat(notification.getStatus()).isEqualTo(EmailNotificationStatus.SENT);
    assertThat(notification.getProvider()).isEqualTo("noop");
  }

  @Test
  void providerFailureSchedulesRetry() {
    EmailNotification notification = notification();
    when(repository.findDueNotifications(
            eq(EmailNotificationStatus.PENDING.name()), any(), any(Integer.class)))
        .thenReturn(List.of(notification));
    when(provider.send(any())).thenThrow(new EmailProviderException("provider down"));

    service.processDueNotifications();

    assertThat(notification.getStatus()).isEqualTo(EmailNotificationStatus.PENDING);
    assertThat(notification.getAttemptCount()).isEqualTo(1);
    assertThat(notification.getNextAttemptAt()).isNotNull();
  }

  @Test
  void providerFailureMarksFailedAfterMaxAttempts() {
    EmailNotification notification = notification();
    for (int i = 0; i < 4; i++) {
      notification.markRetry("previous failure", Instant.now(), Instant.now());
    }
    when(repository.findDueNotifications(
            eq(EmailNotificationStatus.PENDING.name()), any(), any(Integer.class)))
        .thenReturn(List.of(notification));
    when(provider.send(any())).thenThrow(new EmailProviderException("provider down"));

    service.processDueNotifications();

    assertThat(notification.getStatus()).isEqualTo(EmailNotificationStatus.FAILED);
    assertThat(notification.getAttemptCount()).isEqualTo(5);
    assertThat(notification.getFailedAt()).isNotNull();
    assertThat(notification.getNextAttemptAt()).isNull();
  }

  @Test
  void enqueueRendersTemplateAndSavesPendingNotification() {
    when(repository.findByIdempotencyKey("workspace-invitation:1")).thenReturn(Optional.empty());
    when(renderer.render(any(), any()))
        .thenReturn(new EmailTemplateRenderer.RenderedEmail("body text", "body html"));

    var response = service.enqueue(request("workspace-invitation:1"));

    assertThat(response.status()).isEqualTo(EmailNotificationStatus.PENDING);
    verify(repository).save(any(EmailNotification.class));
  }

  @Test
  void suppressedRecipientIsRejectedBeforeProviderSend() {
    when(repository.findByIdempotencyKey("workspace-invitation:1")).thenReturn(Optional.empty());
    when(renderer.render(any(), any()))
        .thenReturn(new EmailTemplateRenderer.RenderedEmail("body text", "body html"));
    when(suppressionService.active("user@example.com"))
        .thenReturn(
            Optional.of(
                mock(com.notebook.lumen.notification.email.suppression.EmailSuppression.class)));

    assertThatThrownBy(() -> service.enqueue(request("workspace-invitation:1")))
        .isInstanceOf(NotificationException.class)
        .extracting("errorCode")
        .isEqualTo("EMAIL_RECIPIENT_SUPPRESSED");

    verify(repository, never()).save(any());
    verify(provider, never()).send(any());
  }

  @Test
  void workerDisabledDoesNotFetchDueNotifications() {
    EmailNotificationService disabledService =
        new EmailNotificationService(
            repository,
            renderer,
            provider,
            suppressionService,
            disabledProperties(),
            auditService,
            preferenceResolver,
            deliveryPreferenceService,
            digestService,
            new SimpleMeterRegistry(),
            analyticsRecorder);

    disabledService.processDueNotifications();

    verify(repository, never()).findDueNotifications(any(), any(), any(Integer.class));
  }

  @Test
  void expiredSendingNotificationIsRecoveredBeforeClaim() {
    EmailNotification stale = notification();
    Instant now = Instant.now();
    stale.markSending("old-worker", now.minusSeconds(600), now.minusSeconds(300));
    when(repository.findExpiredSendingForUpdate(
            eq(EmailNotificationStatus.SENDING.name()), any(), any(Integer.class)))
        .thenReturn(List.of(stale));
    when(repository.findDueNotifications(
            eq(EmailNotificationStatus.PENDING.name()), any(), any(Integer.class)))
        .thenReturn(List.of());

    service.processDueNotifications();

    assertThat(stale.getStatus()).isEqualTo(EmailNotificationStatus.PENDING);
    assertThat(stale.getLockedBy()).isNull();
    assertThat(stale.getLockExpiresAt()).isNull();
    assertThat(stale.getNextAttemptAt()).isNotNull();
  }

  private EmailNotification notification() {
    return new EmailNotification(
        UUID.randomUUID(),
        EmailNotificationType.WORKSPACE_INVITATION,
        "user@example.com",
        "Subject",
        "text",
        "html",
        "workspace-invitation:1",
        Instant.now());
  }

  private EmailNotificationRequest request(String idempotencyKey) {
    return new EmailNotificationRequest(
        EmailNotificationType.WORKSPACE_INVITATION,
        "USER@example.com",
        "Subject",
        "workspace-invitation",
        Map.of(
            "workspaceName",
            "workspace",
            "inviterEmail",
            "owner@example.com",
            "role",
            "ADMIN",
            "acceptUrl",
            "https://example.test/accept"),
        idempotencyKey,
        null,
        null);
  }

  private NotificationProperties properties() {
    return new NotificationProperties(
        "",
        new NotificationProperties.Email(
            "noop",
            "no-reply@example.com",
            "",
            true,
            5,
            60,
            3600,
            5000,
            25,
            300,
            new NotificationProperties.Smtp("localhost", 587, "", "", true),
            new NotificationProperties.GenericHttp("", "", "Authorization", 1000, 3000),
            new NotificationProperties.Webhooks(
                false,
                "generic-http",
                "",
                "X-Email-Signature",
                "X-Email-Timestamp",
                300,
                false,
                false)),
        new NotificationProperties.Internal(
            new NotificationProperties.TrustedService(
                "", "", "", "workspace-service", "notification-service", 5, ""),
            null,
            null),
        new NotificationProperties.InApp(true),
        new NotificationProperties.Preferences(true),
        new NotificationProperties.Digest(true, true, 60, 100, 50, "09:00", java.time.DayOfWeek.MONDAY, "09:00"),
        NotificationTestFanout.disabled(),
        NotificationTestWorkspace.disabled());
  }

  private NotificationProperties disabledProperties() {
    return new NotificationProperties(
        "",
        new NotificationProperties.Email(
            "noop",
            "no-reply@example.com",
            "",
            false,
            5,
            60,
            3600,
            5000,
            25,
            300,
            new NotificationProperties.Smtp("localhost", 587, "", "", true),
            new NotificationProperties.GenericHttp("", "", "Authorization", 1000, 3000),
            new NotificationProperties.Webhooks(
                false,
                "generic-http",
                "",
                "X-Email-Signature",
                "X-Email-Timestamp",
                300,
                false,
                false)),
        new NotificationProperties.Internal(
            new NotificationProperties.TrustedService(
                "", "", "", "workspace-service", "notification-service", 5, ""),
            null,
            null),
        new NotificationProperties.InApp(true),
        new NotificationProperties.Preferences(true),
        new NotificationProperties.Digest(true, true, 60, 100, 50, "09:00", java.time.DayOfWeek.MONDAY, "09:00"),
        NotificationTestFanout.disabled(),
        NotificationTestWorkspace.disabled());
  }
}
