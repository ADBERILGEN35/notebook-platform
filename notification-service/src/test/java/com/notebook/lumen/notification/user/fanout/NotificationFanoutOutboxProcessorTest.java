package com.notebook.lumen.notification.user.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import com.notebook.lumen.notification.user.realtime.NotificationInstanceIdProvider;
import com.notebook.lumen.notification.user.realtime.NotificationSseBroker;
import com.notebook.lumen.notification.user.realtime.NotificationSseDistributedPublisher;
import com.notebook.lumen.notification.user.realtime.NotificationSseEventDispatcher;
import com.notebook.lumen.notification.user.realtime.NotificationSseEventEnvelope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationFanoutOutboxProcessorTest {

  private final NotificationFanoutOutboxRepository repository =
      mock(NotificationFanoutOutboxRepository.class);
  private final NotificationProperties properties = mock(NotificationProperties.class);
  private final NotificationSseBroker broker = mock(NotificationSseBroker.class);
  private final NotificationSseDistributedPublisher distributedPublisher =
      mock(NotificationSseDistributedPublisher.class);
  private final NotificationInstanceIdProvider instanceIdProvider =
      mock(NotificationInstanceIdProvider.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final NotificationAnalyticsRecorder analyticsRecorder =
      mock(NotificationAnalyticsRecorder.class);
  private NotificationSseEventDispatcher sseDispatcher;
  private NotificationFanoutOutboxProcessor processor;

  @BeforeEach
  void setUp() {
    when(instanceIdProvider.instanceId()).thenReturn("worker-1");
    NotificationSseProperties sseProperties = new NotificationSseProperties();
    sseProperties.getDistributed().setEnabled(true);
    sseProperties.getDistributed().setPublishLocalFirst(false);
    sseDispatcher =
        new NotificationSseEventDispatcher(
            broker,
            distributedPublisher,
            sseProperties,
            instanceIdProvider,
            meterRegistry,
            analyticsRecorder);
    processor =
        new NotificationFanoutOutboxProcessor(
            repository, properties, sseDispatcher, meterRegistry, analyticsRecorder);
    when(properties.fanout())
        .thenReturn(
            new NotificationProperties.Fanout(true, true, true, 5, 100, 10, 5, 300, 60, 24, 30));
    when(repository.findExpiredSendingForUpdate(anyString(), any(), anyInt()))
        .thenReturn(List.of());
  }

  @Test
  void publishesAndMarksSent() {
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    NotificationFanoutOutbox row =
        new NotificationFanoutOutbox(
            UUID.randomUUID(),
            eventId,
            userId,
            "notification.created",
            Map.of("a", 1),
            Instant.now());
    when(repository.findDuePendingForUpdate(anyString(), any(), anyInt())).thenReturn(List.of(row));

    processor.processDue();

    assertThat(row.getStatus()).isEqualTo(NotificationFanoutOutboxStatus.SENT);
    assertThat(row.getSentAt()).isNotNull();
    verify(distributedPublisher).publish(any(NotificationSseEventEnvelope.class));
  }

  @Test
  void publishFailureSchedulesRetry() {
    org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
        .when(distributedPublisher)
        .publish(any());
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    NotificationFanoutOutbox row =
        new NotificationFanoutOutbox(
            UUID.randomUUID(), eventId, userId, "notification.created", Map.of(), Instant.now());
    when(repository.findDuePendingForUpdate(anyString(), any(), anyInt())).thenReturn(List.of(row));

    processor.processDue();

    assertThat(row.getStatus()).isEqualTo(NotificationFanoutOutboxStatus.PENDING);
    assertThat(row.getAttemptCount()).isEqualTo(1);
    assertThat(row.getNextAttemptAt()).isAfter(Instant.now());
  }

  @Test
  void maxAttemptsMarksDead() {
    org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
        .when(distributedPublisher)
        .publish(any());
    when(properties.fanout())
        .thenReturn(
            new NotificationProperties.Fanout(true, true, true, 5, 100, 1, 5, 300, 60, 24, 30));
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    NotificationFanoutOutbox row =
        new NotificationFanoutOutbox(
            UUID.randomUUID(), eventId, userId, "notification.created", Map.of(), Instant.now());
    when(repository.findDuePendingForUpdate(anyString(), any(), anyInt())).thenReturn(List.of(row));

    processor.processDue();

    assertThat(row.getStatus()).isEqualTo(NotificationFanoutOutboxStatus.DEAD);
  }

  @Test
  void rehydratesEnvelopeWithSameEventId() {
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant created = Instant.parse("2024-01-01T00:00:00Z");
    NotificationFanoutOutbox row =
        new NotificationFanoutOutbox(
            UUID.randomUUID(),
            eventId,
            userId,
            "notification.read",
            Map.of("unreadCount", 2L),
            created);
    when(repository.findDuePendingForUpdate(anyString(), any(), anyInt())).thenReturn(List.of(row));
    ArgumentCaptor<NotificationSseEventEnvelope> cap =
        ArgumentCaptor.forClass(NotificationSseEventEnvelope.class);

    processor.processDue();

    verify(distributedPublisher).publish(cap.capture());
    assertThat(cap.getValue().eventId()).isEqualTo(eventId);
    assertThat(cap.getValue().recipientUserId()).isEqualTo(userId);
    assertThat(cap.getValue().eventType()).isEqualTo("notification.read");
    assertThat(cap.getValue().originInstanceId()).contains("notification-fanout");
  }
}
