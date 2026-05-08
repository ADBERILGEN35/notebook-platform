package com.notebook.lumen.notification.user.realtime;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Component
@ConditionalOnMissingBean(NotificationSseDistributedPublisher.class)
public class NoopNotificationSseDistributedPublisher implements NotificationSseDistributedPublisher {
  @Override
  public void publish(NotificationSseEventEnvelope envelope) {
    // no-op when distributed fanout is disabled
  }
}
