package com.notebook.lumen.notification.user.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(name = "notification.sse.distributed.enabled", havingValue = "true")
public class NotificationSseRedisSubscriber {
  @Bean
  RedisMessageListenerContainer notificationSseRedisListenerContainer(
      RedisConnectionFactory connectionFactory,
      NotificationSseProperties sseProperties,
      NotificationSseDistributedMessageHandler messageHandler) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    MessageListener listener =
        (Message message, byte[] pattern) ->
            messageHandler.handleMessage(new String(message.getBody(), StandardCharsets.UTF_8));
    container.addMessageListener(listener, new ChannelTopic(sseProperties.getDistributed().getChannel()));
    return container;
  }
}
