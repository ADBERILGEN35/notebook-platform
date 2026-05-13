package com.notebook.lumen.identity.admin.retention;

public enum PlatformLegalHoldScope {
  ALL_PLATFORM,
  CONTENT,
  IDENTITY,
  AUDIT,
  NOTIFICATION,
  WORKSPACE,
  USER,
  NOTE;

  public boolean blocks(RetentionTargetDefinition target) {
    return switch (this) {
      case ALL_PLATFORM -> true;
      case CONTENT, WORKSPACE, NOTE -> target.dataClass() == RetentionDataClass.CONTENT;
      case IDENTITY, USER -> target.dataClass() == RetentionDataClass.IDENTITY;
      case AUDIT -> target.dataClass() == RetentionDataClass.AUDIT_SECURITY;
      case NOTIFICATION -> target.dataClass() == RetentionDataClass.NOTIFICATION;
    };
  }
}
