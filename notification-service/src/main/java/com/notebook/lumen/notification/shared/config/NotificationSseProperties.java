package com.notebook.lumen.notification.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "notification.sse")
public class NotificationSseProperties {
  private boolean enabled = true;
  private long heartbeatSeconds = 25;
  private long timeoutSeconds = 0;
  private int maxConnectionsPerUser = 5;
  private Distributed distributed = new Distributed();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getHeartbeatSeconds() {
    return heartbeatSeconds;
  }

  public void setHeartbeatSeconds(long heartbeatSeconds) {
    this.heartbeatSeconds = heartbeatSeconds;
  }

  public long getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  public int getMaxConnectionsPerUser() {
    return maxConnectionsPerUser;
  }

  public void setMaxConnectionsPerUser(int maxConnectionsPerUser) {
    this.maxConnectionsPerUser = maxConnectionsPerUser;
  }

  public Distributed getDistributed() {
    return distributed;
  }

  public void setDistributed(Distributed distributed) {
    this.distributed = distributed;
  }

  public static class Distributed {
    private boolean enabled = false;
    private String provider = "redis";
    private String channel = "notification:sse:events";
    private boolean publishLocalFirst = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getChannel() {
      return channel;
    }

    public void setChannel(String channel) {
      this.channel = channel;
    }

    public boolean isPublishLocalFirst() {
      return publishLocalFirst;
    }

    public void setPublishLocalFirst(boolean publishLocalFirst) {
      this.publishLocalFirst = publishLocalFirst;
    }
  }
}
