package com.notebook.lumen.notification.user.realtime;

import java.net.InetAddress;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationInstanceIdProvider {
  private final String instanceId;

  public NotificationInstanceIdProvider(
      @Value("${notification.instance-id:}") String configuredInstanceId) {
    if (configuredInstanceId != null && !configuredInstanceId.isBlank()) {
      this.instanceId = configuredInstanceId.trim();
      return;
    }
    this.instanceId = defaultInstanceId();
  }

  public String instanceId() {
    return instanceId;
  }

  private String defaultInstanceId() {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
    } catch (Exception ex) {
      return "notification-" + UUID.randomUUID().toString().substring(0, 8);
    }
  }
}
