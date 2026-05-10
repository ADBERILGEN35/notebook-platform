package com.notebook.lumen.notification.admin.legalhold;

public final class LegalHoldAuditEventType {

  private LegalHoldAuditEventType() {}

  public static final String VIEWED = "ADMIN_NOTIFICATION_LEGAL_HOLD_VIEWED";
  public static final String CREATED = "ADMIN_NOTIFICATION_LEGAL_HOLD_CREATED";
  public static final String RELEASED = "ADMIN_NOTIFICATION_LEGAL_HOLD_RELEASED";
  public static final String CREATE_DENIED = "ADMIN_NOTIFICATION_LEGAL_HOLD_CREATE_DENIED";
  public static final String RELEASE_DENIED = "ADMIN_NOTIFICATION_LEGAL_HOLD_RELEASE_DENIED";
  public static final String RETENTION_BLOCKED = "NOTIFICATION_RETENTION_BLOCKED_BY_LEGAL_HOLD";
}
