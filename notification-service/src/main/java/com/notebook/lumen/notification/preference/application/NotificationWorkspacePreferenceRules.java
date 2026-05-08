package com.notebook.lumen.notification.preference.application;

import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.EnumSet;
import java.util.Set;

public final class NotificationWorkspacePreferenceRules {
  private NotificationWorkspacePreferenceRules() {}

  private static final Set<UserNotificationType> OVERRIDABLE =
      EnumSet.of(
          UserNotificationType.WORKSPACE_INVITATION_RECEIVED,
          UserNotificationType.COMMENT_ADDED,
          UserNotificationType.NOTE_VERSION_RESTORED,
          UserNotificationType.SYSTEM_NOTICE);

  public static boolean supportsWorkspaceOverride(UserNotificationType type) {
    return OVERRIDABLE.contains(type);
  }

  public static boolean isMandatorySecurityType(UserNotificationType type) {
    return type == UserNotificationType.SECURITY_SESSIONS_REVOKED;
  }

  public static Set<UserNotificationType> overridableTypes() {
    return OVERRIDABLE;
  }
}
