package com.notebook.lumen.notification;

import com.notebook.lumen.notification.shared.config.NotificationProperties;

public final class NotificationTestWorkspace {
  private NotificationTestWorkspace() {}

  public static NotificationProperties.WorkspaceClient disabled() {
    return new NotificationProperties.WorkspaceClient(
        false,
        false,
        false,
        "http://localhost:8082",
        3000,
        new NotificationProperties.OutboundServiceJwt(
            "", "", "", "", "", "", 60, "workspace-service"));
  }
}
