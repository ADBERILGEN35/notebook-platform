package com.notebook.lumen.notification.user.fanout;

public enum NotificationFanoutOutboxStatus {
  PENDING,
  SENDING,
  SENT,
  FAILED,
  DEAD
}
