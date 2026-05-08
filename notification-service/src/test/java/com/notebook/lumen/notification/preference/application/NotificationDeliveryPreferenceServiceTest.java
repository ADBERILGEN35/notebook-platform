package com.notebook.lumen.notification.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.preference.domain.EmailDigestFrequency;
import com.notebook.lumen.notification.preference.infrastructure.UserNotificationDeliveryPreferenceRepository;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDeliveryPreferenceServiceTest {
  private final UserNotificationDeliveryPreferenceRepository repository = mock(UserNotificationDeliveryPreferenceRepository.class);
  private final AuditService auditService = mock(AuditService.class);
  private final NotificationDeliveryPreferenceService service =
      new NotificationDeliveryPreferenceService(repository, auditService);

  NotificationDeliveryPreferenceServiceTest() {
    when(repository.findByUserId(any())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void rejectsInvalidTimezone() {
    assertThatThrownBy(
            () ->
                service.update(
                    UUID.randomUUID(),
                    true,
                    EmailDigestFrequency.DAILY,
                    true,
                    LocalTime.of(22, 0),
                    LocalTime.of(8, 0),
                    "Bad/TZ"))
        .isInstanceOf(NotificationException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_NOTIFICATION_TIMEZONE");
  }

  @Test
  void crossingMidnightQuietHoursIsDetected() {
    var pref =
        service.update(
            UUID.randomUUID(),
            false,
            EmailDigestFrequency.DAILY,
            true,
            LocalTime.of(22, 0),
            LocalTime.of(8, 0),
            "UTC");
    assertThat(service.isQuietHoursNow(pref, Instant.parse("2026-01-01T23:00:00Z"))).isTrue();
    assertThat(service.isQuietHoursNow(pref, Instant.parse("2026-01-01T12:00:00Z"))).isFalse();
  }
}
