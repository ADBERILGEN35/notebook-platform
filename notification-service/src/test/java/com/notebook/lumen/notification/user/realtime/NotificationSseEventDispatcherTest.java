package com.notebook.lumen.notification.user.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationSseEventDispatcherTest {

  @Test
  void localFirstDispatchesLocalAndPublishesDistributed() {
    NotificationSseBroker broker = mock(NotificationSseBroker.class);
    NotificationSseDistributedPublisher publisher = mock(NotificationSseDistributedPublisher.class);
    NotificationInstanceIdProvider instanceIdProvider = mock(NotificationInstanceIdProvider.class);
    when(instanceIdProvider.instanceId()).thenReturn("instance-a");

    NotificationSseProperties properties = new NotificationSseProperties();
    properties.getDistributed().setEnabled(true);
    properties.getDistributed().setPublishLocalFirst(true);

    NotificationAnalyticsRecorder analyticsRecorder = mock(NotificationAnalyticsRecorder.class);
    NotificationSseEventDispatcher dispatcher =
        new NotificationSseEventDispatcher(
            broker,
            publisher,
            properties,
            instanceIdProvider,
            new SimpleMeterRegistry(),
            analyticsRecorder);

    dispatcher.publishUnreadCount(UUID.randomUUID(), 3);

    verify(broker)
        .deliverToUser(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("notification.unread_count"),
            org.mockito.ArgumentMatchers.anyMap());
    verify(publisher).publish(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void disabledDistributedDoesNotCallPublisher() {
    NotificationSseBroker broker = mock(NotificationSseBroker.class);
    NotificationSseDistributedPublisher publisher = mock(NotificationSseDistributedPublisher.class);
    NotificationInstanceIdProvider instanceIdProvider = mock(NotificationInstanceIdProvider.class);
    when(instanceIdProvider.instanceId()).thenReturn("instance-a");

    NotificationSseProperties properties = new NotificationSseProperties();
    properties.getDistributed().setEnabled(false);

    NotificationAnalyticsRecorder analyticsRecorder = mock(NotificationAnalyticsRecorder.class);
    NotificationSseEventDispatcher dispatcher =
        new NotificationSseEventDispatcher(
            broker,
            publisher,
            properties,
            instanceIdProvider,
            new SimpleMeterRegistry(),
            analyticsRecorder);

    dispatcher.publishUnreadCount(UUID.randomUUID(), 2);

    verify(broker)
        .deliverToUser(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("notification.unread_count"),
            org.mockito.ArgumentMatchers.anyMap());
    verifyNoInteractions(publisher);
  }
}
