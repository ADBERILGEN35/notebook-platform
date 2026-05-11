package com.notebook.lumen.notification.user.realtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationSseBrokerTest {

  @Test
  void rejectsWhenMaxConnectionsReached() {
    NotificationSseProperties properties = new NotificationSseProperties();
    properties.setEnabled(true);
    properties.setMaxConnectionsPerUser(1);
    properties.setHeartbeatSeconds(60);

    NotificationSseBroker broker =
        new NotificationSseBroker(
            properties, new SimpleMeterRegistry(), mock(NotificationAnalyticsRecorder.class));
    UUID userId = UUID.randomUUID();
    broker.connect(userId);

    assertThatThrownBy(() -> broker.connect(userId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Too many active SSE connections");
  }
}
