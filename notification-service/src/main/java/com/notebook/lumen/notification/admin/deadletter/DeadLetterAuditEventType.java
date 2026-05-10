package com.notebook.lumen.notification.admin.deadletter;

public final class DeadLetterAuditEventType {

  private DeadLetterAuditEventType() {}

  public static final String VIEWED = "ADMIN_NOTIFICATION_DEAD_LETTER_VIEWED";
  public static final String REQUEUE_DRY_RUN = "ADMIN_NOTIFICATION_DEAD_LETTER_REQUEUE_DRY_RUN";
  public static final String REQUEUED = "ADMIN_NOTIFICATION_DEAD_LETTER_REQUEUED";
  public static final String REQUEUE_DENIED = "ADMIN_NOTIFICATION_DEAD_LETTER_REQUEUE_DENIED";
}
