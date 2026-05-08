package com.notebook.lumen.notification.user.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationSseDistributedMessageHandler {
  private static final Logger log = LoggerFactory.getLogger(NotificationSseDistributedMessageHandler.class);

  private final ObjectMapper objectMapper;
  private final NotificationSseEventDispatcher dispatcher;
  private final NotificationInstanceIdProvider instanceIdProvider;
  private final NotificationSseProperties sseProperties;
  private final MeterRegistry meterRegistry;

  public NotificationSseDistributedMessageHandler(
      ObjectMapper objectMapper,
      NotificationSseEventDispatcher dispatcher,
      NotificationInstanceIdProvider instanceIdProvider,
      NotificationSseProperties sseProperties,
      MeterRegistry meterRegistry) {
    this.objectMapper = objectMapper;
    this.dispatcher = dispatcher;
    this.instanceIdProvider = instanceIdProvider;
    this.sseProperties = sseProperties;
    this.meterRegistry = meterRegistry;
  }

  public void handleMessage(String payload) {
    try {
      NotificationSseEventEnvelope envelope =
          objectMapper.readValue(payload, NotificationSseEventEnvelope.class);
      meterRegistry.counter("notifications_sse_distributed_received_total").increment();
      if (sseProperties.getDistributed().isPublishLocalFirst()
          && instanceIdProvider.instanceId().equals(envelope.originInstanceId())) {
        meterRegistry.counter("notifications_sse_distributed_skipped_self_total").increment();
        return;
      }
      dispatcher.deliverFromDistributed(envelope);
    } catch (Exception ex) {
      meterRegistry.counter("notifications_sse_distributed_invalid_messages_total").increment();
      meterRegistry.counter("notifications_sse_distributed_subscriber_errors_total").increment();
      log.warn("Failed to consume distributed SSE event");
    }
  }
}
