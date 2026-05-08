package com.notebook.lumen.notification.user.realtime;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisNotificationSseDistributedPublisherTest {

  @Test
  void publishesSerializedEnvelopeToConfiguredChannel() {
    StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
    NotificationSseProperties properties = new NotificationSseProperties();
    properties.getDistributed().setChannel("notification:sse:events");

    RedisNotificationSseDistributedPublisher publisher =
        new RedisNotificationSseDistributedPublisher(
            template, new ObjectMapper().findAndRegisterModules(), properties);

    publisher.publish(
        new NotificationSseEventEnvelope(
            UUID.randomUUID(),
            "instance-a",
            UUID.randomUUID(),
            "notification.unread_count",
            Map.of("unreadCount", 5),
            Instant.now()));

    verify(template).convertAndSend(Mockito.eq("notification:sse:events"), Mockito.anyString());
  }
}
