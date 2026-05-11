package com.notebook.lumen.notification.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.email.domain.NotificationDigestItemStatus;
import com.notebook.lumen.notification.email.infrastructure.NotificationDigestItemRepository;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxRepository;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxStatus;
import com.notebook.lumen.notification.user.realtime.NotificationSseBroker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationAnalyticsSummaryServiceTest {

  @Mock private NotificationDeliveryAnalyticsHourlyRepository hourlyRepository;
  @Mock private NotificationFanoutOutboxRepository fanoutOutboxRepository;
  @Mock private NotificationDigestItemRepository digestItemRepository;
  @Mock private NotificationSseBroker sseBroker;
  @Mock private NotificationWorkerRunTimestamps workerRunTimestamps;

  private NotificationAnalyticsProperties analyticsProperties;
  private NotificationProperties notificationProperties;
  private SimpleMeterRegistry meterRegistry;
  private NotificationAnalyticsSummaryService service;

  @BeforeEach
  void setUp() {
    analyticsProperties = new NotificationAnalyticsProperties(true, 90, 30, "hour");
    notificationProperties = minimalNotificationProperties();
    meterRegistry = new SimpleMeterRegistry();
    meterRegistry.counter("notifications_sse_events_sent_total").increment(3);
    service =
        new NotificationAnalyticsSummaryService(
            analyticsProperties,
            hourlyRepository,
            fanoutOutboxRepository,
            digestItemRepository,
            notificationProperties,
            sseBroker,
            workerRunTimestamps,
            meterRegistry);
  }

  private static NotificationProperties minimalNotificationProperties() {
    var email =
        new NotificationProperties.Email(
            "log", "f", "r", true, 5, 60, 3600, 1000L, 25, 300L, null, null, null);
    var ts = new NotificationProperties.TrustedService("k", null, "/p", "iss", "aud", 5, "scope");
    var internal = new NotificationProperties.Internal(ts, ts, ts);
    var digest =
        new NotificationProperties.Digest(
            true, true, 60, 100, 50, "09:00", DayOfWeek.MONDAY, "09:00");
    var fanout =
        new NotificationProperties.Fanout(true, true, true, 5, 100, 10, 5, 300, 60, 24, 30);
    var outbound =
        new NotificationProperties.OutboundServiceJwt(
            "k", "", "/p", "iss", "sub", "svc", 60, "aud");
    var workspace =
        new NotificationProperties.WorkspaceClient(
            false, false, false, "http://localhost", 3000, outbound);
    return new NotificationProperties(
        "w1",
        email,
        internal,
        new NotificationProperties.InApp(true),
        new NotificationProperties.Preferences(true),
        digest,
        fanout,
        workspace);
  }

  private static NotificationDeliveryAnalyticsHourlyRepository.EventKindTotalProjection kindTotal(
      NotificationAnalyticsEventKind kind, long total) {
    var m = mock(NotificationDeliveryAnalyticsHourlyRepository.EventKindTotalProjection.class);
    when(m.getEventKind()).thenReturn(kind);
    when(m.getTotal()).thenReturn(total);
    return m;
  }

  private static NotificationDeliveryAnalyticsHourlyRepository.ChannelEventKindTotalProjection
      channelKind(String channel, NotificationAnalyticsEventKind kind, long total) {
    var m =
        mock(NotificationDeliveryAnalyticsHourlyRepository.ChannelEventKindTotalProjection.class);
    when(m.getChannel()).thenReturn(channel);
    when(m.getEventKind()).thenReturn(kind);
    when(m.getTotal()).thenReturn(total);
    return m;
  }

  private static NotificationDeliveryAnalyticsHourlyRepository.TypeEventKindTotalProjection
      typeKind(String type, NotificationAnalyticsEventKind kind, long total) {
    var m = mock(NotificationDeliveryAnalyticsHourlyRepository.TypeEventKindTotalProjection.class);
    when(m.getNotificationType()).thenReturn(type);
    when(m.getEventKind()).thenReturn(kind);
    when(m.getTotal()).thenReturn(total);
    return m;
  }

  @Test
  void build_mapsKindTotalsFanoutAndSse() {
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    Instant to = Instant.parse("2026-05-02T00:00:00Z");
    var kindRows =
        List.of(
            kindTotal(NotificationAnalyticsEventKind.CREATED, 2),
            kindTotal(NotificationAnalyticsEventKind.QUEUED, 1),
            kindTotal(NotificationAnalyticsEventKind.SENT, 4),
            kindTotal(NotificationAnalyticsEventKind.SKIPPED_PREFERENCE, 10),
            kindTotal(NotificationAnalyticsEventKind.SKIPPED_WORKSPACE_PREFERENCE, 3),
            kindTotal(NotificationAnalyticsEventKind.SKIPPED_WORKSPACE_ADMIN_POLICY, 2),
            kindTotal(NotificationAnalyticsEventKind.SSE_SEND_FAILURE, 1));
    var channelRows =
        List.of(
            channelKind("IN_APP", NotificationAnalyticsEventKind.CREATED, 2),
            channelKind("EMAIL", NotificationAnalyticsEventKind.QUEUED, 1));
    var typeRows = List.of(typeKind("COMMENT_ADDED", NotificationAnalyticsEventKind.CREATED, 2));
    when(hourlyRepository.sumByEventKind(from, to)).thenReturn(kindRows);
    when(hourlyRepository.sumByChannelAndEventKind(from, to)).thenReturn(channelRows);
    when(hourlyRepository.sumByTypeAndEventKind(from, to)).thenReturn(typeRows);
    when(fanoutOutboxRepository.countByStatus(NotificationFanoutOutboxStatus.PENDING))
        .thenReturn(5L);
    when(fanoutOutboxRepository.countByStatus(NotificationFanoutOutboxStatus.SENDING))
        .thenReturn(1L);
    when(fanoutOutboxRepository.countByStatus(NotificationFanoutOutboxStatus.DEAD)).thenReturn(2L);
    when(digestItemRepository.countByStatus(NotificationDigestItemStatus.PENDING)).thenReturn(7L);
    when(sseBroker.activeConnectionCount()).thenReturn(11);
    when(workerRunTimestamps.fanoutLastRun()).thenReturn(from);
    when(workerRunTimestamps.digestLastRun()).thenReturn(from);
    when(workerRunTimestamps.emailLastRun()).thenReturn(from);

    var resp = service.build(from, to);

    assertThat(resp.totals().created()).isEqualTo(3L);
    assertThat(resp.totals().sent()).isEqualTo(4L);
    assertThat(resp.totals().skippedPreference()).isEqualTo(15L);
    assertThat(resp.fanout().pending()).isEqualTo(5L);
    assertThat(resp.fanout().retrying()).isEqualTo(1L);
    assertThat(resp.fanout().dead()).isEqualTo(2L);
    assertThat(resp.sse().activeConnections()).isEqualTo(11);
    assertThat(resp.sse().sendFailuresInRange()).isEqualTo(1L);
    assertThat(resp.sse().eventsSentMeterTotal()).isEqualTo(3L);
    assertThat(resp.digest().pendingItems()).isEqualTo(7L);
    assertThat(resp.byType()).hasSize(1);
    assertThat(resp.byType().getFirst().notificationType()).isEqualTo("COMMENT_ADDED");
  }

  @Test
  void validateRange_rejectsWhenTooLarge() {
    analyticsProperties = new NotificationAnalyticsProperties(true, 90, 2, "hour");
    service =
        new NotificationAnalyticsSummaryService(
            analyticsProperties,
            hourlyRepository,
            fanoutOutboxRepository,
            digestItemRepository,
            notificationProperties,
            sseBroker,
            workerRunTimestamps,
            meterRegistry);
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    Instant to = from.plus(3, ChronoUnit.DAYS);
    assertThatThrownBy(() -> service.validateRange(from, to))
        .isInstanceOf(NotificationException.class);
  }

  @Test
  void validateRange_rejectsNonPositiveSpan() {
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    assertThatThrownBy(() -> service.validateRange(from, from))
        .isInstanceOf(NotificationException.class);
  }

  @Test
  void build_usesEmptyHourlyWhenNoRows() {
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    Instant to = Instant.parse("2026-05-02T00:00:00Z");
    when(hourlyRepository.sumByEventKind(from, to)).thenReturn(List.of());
    when(hourlyRepository.sumByChannelAndEventKind(from, to)).thenReturn(List.of());
    when(hourlyRepository.sumByTypeAndEventKind(from, to)).thenReturn(List.of());
    when(fanoutOutboxRepository.countByStatus(any())).thenReturn(0L);
    when(digestItemRepository.countByStatus(any())).thenReturn(0L);
    when(sseBroker.activeConnectionCount()).thenReturn(0);
    when(workerRunTimestamps.fanoutLastRun()).thenReturn(null);
    when(workerRunTimestamps.digestLastRun()).thenReturn(null);
    when(workerRunTimestamps.emailLastRun()).thenReturn(null);

    var resp = service.build(from, to);

    assertThat(resp.totals().created()).isZero();
    assertThat(resp.byChannel()).isEmpty();
    assertThat(resp.byType()).isEmpty();
  }
}
