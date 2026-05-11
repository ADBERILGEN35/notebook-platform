package com.notebook.lumen.notification.email.application;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsEventKind;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.email.domain.EmailNotification;
import com.notebook.lumen.notification.email.domain.EmailNotificationType;
import com.notebook.lumen.notification.email.domain.NotificationDigestItem;
import com.notebook.lumen.notification.email.domain.NotificationDigestItemStatus;
import com.notebook.lumen.notification.email.infrastructure.EmailNotificationRepository;
import com.notebook.lumen.notification.email.infrastructure.NotificationDigestItemRepository;
import com.notebook.lumen.notification.preference.application.NotificationDeliveryPreferenceService;
import com.notebook.lumen.notification.preference.domain.UserNotificationDeliveryPreference;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDigestService {
  private final NotificationDigestItemRepository digestItemRepository;
  private final EmailNotificationRepository emailNotificationRepository;
  private final NotificationDeliveryPreferenceService deliveryPreferenceService;
  private final NotificationProperties properties;
  private final MeterRegistry meterRegistry;
  private final NotificationAnalyticsRecorder analyticsRecorder;

  public NotificationDigestService(
      NotificationDigestItemRepository digestItemRepository,
      EmailNotificationRepository emailNotificationRepository,
      NotificationDeliveryPreferenceService deliveryPreferenceService,
      NotificationProperties properties,
      MeterRegistry meterRegistry,
      NotificationAnalyticsRecorder analyticsRecorder) {
    this.digestItemRepository = digestItemRepository;
    this.emailNotificationRepository = emailNotificationRepository;
    this.deliveryPreferenceService = deliveryPreferenceService;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.analyticsRecorder = analyticsRecorder;
  }

  @Transactional
  public void queueDigestItem(
      UUID userId,
      String recipientEmail,
      UserNotificationType type,
      String title,
      String message,
      String actionUrl,
      Map<String, Object> metadata,
      UUID sourceNotificationId) {
    UserNotificationDeliveryPreference pref = deliveryPreferenceService.get(userId);
    Instant now = Instant.now();
    Instant scheduledFor =
        deliveryPreferenceService.nextDigestTime(
            pref,
            now,
            parseLocalTime(properties.digest().dailySendTime(), LocalTime.of(9, 0)),
            properties.digest().weeklyDay() == null
                ? java.time.DayOfWeek.MONDAY
                : properties.digest().weeklyDay(),
            parseLocalTime(properties.digest().weeklySendTime(), LocalTime.of(9, 0)));
    digestItemRepository.save(
        new NotificationDigestItem(
            UUID.randomUUID(),
            userId,
            recipientEmail,
            type,
            sourceNotificationId,
            title,
            message,
            actionUrl,
            metadata,
            scheduledFor,
            now));
    analyticsRecorder.record(
        NotificationAnalyticsEventKind.DIGEST_QUEUED, type.name(), "EMAIL", "", 1);
  }

  @Transactional
  public void processDueDigestItems() {
    if (!properties.digest().enabled() || !properties.digest().workerEnabled()) {
      return;
    }
    Instant now = Instant.now();
    List<NotificationDigestItem> due =
        digestItemRepository.findDueForUpdate(
            NotificationDigestItemStatus.PENDING.name(), now, properties.digest().batchSize());
    Map<UUID, List<NotificationDigestItem>> grouped =
        due.stream().collect(Collectors.groupingBy(NotificationDigestItem::getUserId));
    int maxItems = Math.max(1, properties.digest().maxItemsPerEmail());
    for (Map.Entry<UUID, List<NotificationDigestItem>> entry : grouped.entrySet()) {
      List<NotificationDigestItem> items = entry.getValue().stream().limit(maxItems).toList();
      UUID emailId = createDigestEmail(items, now);
      for (NotificationDigestItem item : items) {
        item.markSent(emailId, now);
        analyticsRecorder.record(
            NotificationAnalyticsEventKind.DIGEST_SENT,
            item.getNotificationType().name(),
            "EMAIL",
            "",
            1);
      }
    }
    if (!due.isEmpty()) {
      meterRegistry.counter("notification_digest_items_processed_total").increment(due.size());
    }
  }

  private UUID createDigestEmail(List<NotificationDigestItem> items, Instant now) {
    String subject = "Your Notebook Platform updates";
    String body =
        items.stream()
            .map(item -> "- " + item.getTitle() + ": " + item.getMessage())
            .collect(Collectors.joining("\n"));
    EmailNotification email =
        new EmailNotification(
            UUID.randomUUID(),
            EmailNotificationType.NOTIFICATION_DIGEST,
            items.get(0).getRecipientEmail(),
            subject,
            body,
            "<pre>" + body + "</pre>",
            "digest:" + items.get(0).getUserId() + ":" + now.getEpochSecond(),
            now,
            now);
    emailNotificationRepository.save(email);
    return email.getId();
  }

  private LocalTime parseLocalTime(String raw, LocalTime defaultValue) {
    try {
      return raw == null || raw.isBlank() ? defaultValue : LocalTime.parse(raw.trim());
    } catch (DateTimeParseException ex) {
      return defaultValue;
    }
  }
}
