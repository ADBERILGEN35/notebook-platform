package com.notebook.lumen.notification.user.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.sse.distributed.enabled", havingValue = "true")
public class RedisNotificationSseDistributedPublisher implements NotificationSseDistributedPublisher {
  private static final Logger log = LoggerFactory.getLogger(RedisNotificationSseDistributedPublisher.class);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final NotificationSseProperties sseProperties;

  public RedisNotificationSseDistributedPublisher(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      NotificationSseProperties sseProperties) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.sseProperties = sseProperties;
  }

  @Override
  public void publish(NotificationSseEventEnvelope envelope) {
    try {
      String serialized = objectMapper.writeValueAsString(envelope);
      redisTemplate.convertAndSend(sseProperties.getDistributed().getChannel(), serialized);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize SSE distributed event {}", envelope.eventId());
      throw new IllegalStateException("sse_distributed_serialize_failed", e);
    } catch (Exception e) {
      log.warn("Failed to publish SSE distributed event {}", envelope.eventId());
      throw new IllegalStateException("sse_distributed_publish_failed", e);
    }
  }
}
