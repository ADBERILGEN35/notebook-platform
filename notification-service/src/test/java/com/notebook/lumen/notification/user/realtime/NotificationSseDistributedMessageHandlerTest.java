package com.notebook.lumen.notification.user.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationSseDistributedMessageHandlerTest {
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void skipsSelfOriginWhenLocalFirstEnabled() throws Exception {
    NotificationSseEventDispatcher dispatcher = mock(NotificationSseEventDispatcher.class);
    NotificationInstanceIdProvider instanceIdProvider = mock(NotificationInstanceIdProvider.class);
    when(instanceIdProvider.instanceId()).thenReturn("instance-a");

    NotificationSseProperties properties = new NotificationSseProperties();
    properties.getDistributed().setPublishLocalFirst(true);

    NotificationAnalyticsRecorder analyticsRecorder = mock(NotificationAnalyticsRecorder.class);
    NotificationSseDistributedMessageHandler handler =
        new NotificationSseDistributedMessageHandler(
            objectMapper,
            dispatcher,
            instanceIdProvider,
            properties,
            new SimpleMeterRegistry(),
            analyticsRecorder);

    String payload =
        objectMapper.writeValueAsString(
                new NotificationSseEventEnvelope(
                    UUID.randomUUID(),
                    "instance-a",
                    UUID.randomUUID(),
                    "notification.unread_count",
                    Map.of("unreadCount", 1),
                    Instant.now()));
    handler.handleMessage(payload);
    verifyNoInteractions(dispatcher);
  }

  @Test
  void routesForeignOriginToDispatcher() throws Exception {
    NotificationSseEventDispatcher dispatcher = mock(NotificationSseEventDispatcher.class);
    NotificationInstanceIdProvider instanceIdProvider = mock(NotificationInstanceIdProvider.class);
    when(instanceIdProvider.instanceId()).thenReturn("instance-a");

    NotificationSseProperties properties = new NotificationSseProperties();
    properties.getDistributed().setPublishLocalFirst(true);

    NotificationAnalyticsRecorder analyticsRecorder = mock(NotificationAnalyticsRecorder.class);
    NotificationSseDistributedMessageHandler handler =
        new NotificationSseDistributedMessageHandler(
            objectMapper,
            dispatcher,
            instanceIdProvider,
            properties,
            new SimpleMeterRegistry(),
            analyticsRecorder);

    String payload =
        objectMapper.writeValueAsString(
                new NotificationSseEventEnvelope(
                    UUID.randomUUID(),
                    "instance-b",
                    UUID.randomUUID(),
                    "notification.created",
                    Map.of("unreadCount", 2),
                    Instant.now()));
    handler.handleMessage(payload);
    verify(dispatcher).deliverFromDistributed(org.mockito.ArgumentMatchers.any());
  }
}
