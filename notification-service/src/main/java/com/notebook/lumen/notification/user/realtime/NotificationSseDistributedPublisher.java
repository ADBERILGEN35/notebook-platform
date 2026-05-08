package com.notebook.lumen.notification.user.realtime;

public interface NotificationSseDistributedPublisher {
  void publish(NotificationSseEventEnvelope envelope);
}
