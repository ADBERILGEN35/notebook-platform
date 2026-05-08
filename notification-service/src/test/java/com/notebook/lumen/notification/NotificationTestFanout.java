package com.notebook.lumen.notification;

import com.notebook.lumen.notification.shared.config.NotificationProperties;

public final class NotificationTestFanout {
  private NotificationTestFanout() {}

  /** Default for unit tests: outbox off, preserves pre–Faz 64 behavior. */
  public static NotificationProperties.Fanout disabled() {
    return new NotificationProperties.Fanout(false, true, true, 5, 100, 10, 5, 300, 60, 24, 30);
  }
}
