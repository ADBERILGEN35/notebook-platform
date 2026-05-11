package com.notebook.lumen.notification.admin.retention;

public final class RetentionAuditEventType {

  private RetentionAuditEventType() {}

  public static final String PLAN_VIEWED = "ADMIN_NOTIFICATION_RETENTION_PLAN_VIEWED";
  public static final String DRY_RUN = "ADMIN_NOTIFICATION_RETENTION_DRY_RUN";
  public static final String PURGE_STARTED = "ADMIN_NOTIFICATION_RETENTION_PURGE_STARTED";
  public static final String PURGE_COMPLETED = "ADMIN_NOTIFICATION_RETENTION_PURGE_COMPLETED";
  public static final String PURGE_FAILED = "ADMIN_NOTIFICATION_RETENTION_PURGE_FAILED";
  public static final String PURGE_DENIED = "ADMIN_NOTIFICATION_RETENTION_PURGE_DENIED";
  public static final String WORKER_PURGE_COMPLETED =
      "NOTIFICATION_RETENTION_WORKER_PURGE_COMPLETED";
  public static final String WORKER_PURGE_FAILED = "NOTIFICATION_RETENTION_WORKER_PURGE_FAILED";
}
