package com.notebook.lumen.notification.user.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "notification.sse.distributed.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoopNotificationSseDistributedPublisher
    implements NotificationSseDistributedPublisher {
  @Override
  public void publish(NotificationSseEventEnvelope envelope) {
    // no-op when distributed fanout is disabled
  }
}
