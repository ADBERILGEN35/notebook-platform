package com.notebook.lumen.notification.preference.application;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.preference.domain.EmailDigestFrequency;
import com.notebook.lumen.notification.preference.domain.UserNotificationDeliveryPreference;
import com.notebook.lumen.notification.preference.infrastructure.UserNotificationDeliveryPreferenceRepository;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryPreferenceService {
  private final UserNotificationDeliveryPreferenceRepository repository;
  private final AuditService auditService;

  public NotificationDeliveryPreferenceService(
      UserNotificationDeliveryPreferenceRepository repository, AuditService auditService) {
    this.repository = repository;
    this.auditService = auditService;
  }

  @Transactional
  public UserNotificationDeliveryPreference get(UUID userId) {
    return ensure(userId);
  }

  @Transactional
  public UserNotificationDeliveryPreference update(
      UUID userId,
      boolean emailDigestEnabled,
      EmailDigestFrequency emailDigestFrequency,
      boolean quietHoursEnabled,
      LocalTime quietHoursStart,
      LocalTime quietHoursEnd,
      String timezone) {
    ZoneId zoneId = parseZone(timezone);
    validateQuietHours(quietHoursEnabled, quietHoursStart, quietHoursEnd);
    UserNotificationDeliveryPreference pref = ensure(userId);
    pref.update(
        emailDigestEnabled,
        emailDigestFrequency == null ? EmailDigestFrequency.DAILY : emailDigestFrequency,
        quietHoursEnabled,
        quietHoursStart,
        quietHoursEnd,
        zoneId.getId(),
        Instant.now());
    auditService.record(
        "USER_NOTIFICATION_DELIVERY_PREFERENCES_UPDATED",
        "USER_NOTIFICATION_DELIVERY_PREFERENCE",
        userId,
        Map.of("userId", userId.toString()));
    return pref;
  }

  public boolean isQuietHoursNow(UserNotificationDeliveryPreference pref, Instant now) {
    if (!pref.isQuietHoursEnabled()
        || pref.getQuietHoursStart() == null
        || pref.getQuietHoursEnd() == null) {
      return false;
    }
    ZoneId zone = parseZone(pref.getTimezone());
    LocalTime localNow = ZonedDateTime.ofInstant(now, zone).toLocalTime();
    LocalTime start = pref.getQuietHoursStart();
    LocalTime end = pref.getQuietHoursEnd();
    if (start.isBefore(end)) {
      return !localNow.isBefore(start) && localNow.isBefore(end);
    }
    return !localNow.isBefore(start) || localNow.isBefore(end);
  }

  public Instant nextAllowedEmailTime(UserNotificationDeliveryPreference pref, Instant now) {
    if (!isQuietHoursNow(pref, now)) {
      return now;
    }
    ZoneId zone = parseZone(pref.getTimezone());
    ZonedDateTime localNow = ZonedDateTime.ofInstant(now, zone);
    LocalTime end = pref.getQuietHoursEnd();
    LocalDate date = localNow.toLocalDate();
    ZonedDateTime candidate = ZonedDateTime.of(date, end, zone);
    if (!candidate.isAfter(localNow)) {
      candidate = candidate.plusDays(1);
    }
    return candidate.toInstant();
  }

  public Instant nextDigestTime(
      UserNotificationDeliveryPreference pref,
      Instant now,
      LocalTime dailySendTime,
      java.time.DayOfWeek weeklyDay,
      LocalTime weeklyTime) {
    ZoneId zone = parseZone(pref.getTimezone());
    ZonedDateTime localNow = ZonedDateTime.ofInstant(now, zone);
    if (pref.getEmailDigestFrequency() == EmailDigestFrequency.WEEKLY) {
      ZonedDateTime next = localNow.with(java.time.temporal.TemporalAdjusters.nextOrSame(weeklyDay)).with(weeklyTime);
      if (!next.isAfter(localNow)) {
        next = next.plusWeeks(1);
      }
      return next.toInstant();
    }
    ZonedDateTime next = localNow.with(dailySendTime);
    if (!next.isAfter(localNow)) {
      next = next.plusDays(1);
    }
    return next.toInstant();
  }

  private UserNotificationDeliveryPreference ensure(UUID userId) {
    return repository
        .findByUserId(userId)
        .orElseGet(
            () -> repository.save(new UserNotificationDeliveryPreference(UUID.randomUUID(), userId, Instant.now())));
  }

  private ZoneId parseZone(String timezone) {
    try {
      return ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone.trim());
    } catch (DateTimeException ex) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_TIMEZONE", "Invalid timezone");
    }
  }

  private void validateQuietHours(boolean enabled, LocalTime start, LocalTime end) {
    if (!enabled) {
      return;
    }
    if (start == null || end == null) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "INVALID_NOTIFICATION_DELIVERY_PREFERENCE_REQUEST",
          "Quiet hours start/end are required");
    }
    if (start.equals(end)) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "INVALID_QUIET_HOURS_RANGE", "Quiet hours start and end cannot match");
    }
  }
}
