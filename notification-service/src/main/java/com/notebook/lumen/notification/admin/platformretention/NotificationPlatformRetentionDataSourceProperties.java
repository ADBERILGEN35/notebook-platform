package com.notebook.lumen.notification.admin.platformretention;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.platform-retention.datasource")
public record NotificationPlatformRetentionDataSourceProperties(
    boolean enabled, String url, String username, String password) {

  public boolean configComplete() {
    return url != null
        && !url.isBlank()
        && username != null
        && !username.isBlank()
        && password != null;
  }

  public void assertCompleteIfEnabled() {
    if (!enabled) {
      return;
    }
    List<String> missing = new ArrayList<>();
    if (!configComplete()) {
      if (url == null || url.isBlank()) {
        missing.add("url");
      }
      if (username == null || username.isBlank()) {
        missing.add("username");
      }
      if (password == null) {
        missing.add("password");
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "notification.platform-retention.datasource.enabled=true but missing: "
              + String.join(", ", missing)
              + " (set NOTIFICATION_RETENTION_DATASOURCE_URL/USERNAME/PASSWORD)");
    }
  }
}
