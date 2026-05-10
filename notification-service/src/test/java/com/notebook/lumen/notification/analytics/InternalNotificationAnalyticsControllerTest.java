package com.notebook.lumen.notification.analytics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalNotificationAnalyticsControllerTest {

  @Test
  void whenInternalDisabled_throws404() {
    var c =
        new InternalNotificationAnalyticsController(
            new InternalNotificationAnalyticsAdminProperties(false),
            mock(InternalNotificationAuthorizer.class),
            new NotificationAnalyticsProperties(true, 90, 30, "hour"),
            mock(NotificationAnalyticsSummaryService.class));
    Instant from = Instant.now();
    Instant to = from.plusSeconds(60);
    assertThatThrownBy(() -> c.summary("Bearer x", from, to, null))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void whenAnalyticsDisabled_throws404() {
    var c =
        new InternalNotificationAnalyticsController(
            new InternalNotificationAnalyticsAdminProperties(true),
            mock(InternalNotificationAuthorizer.class),
            new NotificationAnalyticsProperties(false, 90, 30, "hour"),
            mock(NotificationAnalyticsSummaryService.class));
    Instant from = Instant.now();
    Instant to = from.plusSeconds(60);
    assertThatThrownBy(() -> c.summary("Bearer x", from, to, null))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void whenEnabled_authorizesValidatesAndBuilds() {
    InternalNotificationAuthorizer auth = mock(InternalNotificationAuthorizer.class);
    NotificationAnalyticsSummaryService svc = mock(NotificationAnalyticsSummaryService.class);
    Instant from = Instant.parse("2026-05-10T10:00:00Z");
    Instant to = from.plusSeconds(3600);
    var c =
        new InternalNotificationAnalyticsController(
            new InternalNotificationAnalyticsAdminProperties(true),
            auth,
            new NotificationAnalyticsProperties(true, 90, 30, "hour"),
            svc);
    c.summary("Bearer t", from, to, "hour");
    verify(auth)
        .authorize(
            "Bearer t", InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_ANALYTICS_SCOPE);
    verify(svc).validateRange(from, to);
    verify(svc).build(from, to);
  }
}
