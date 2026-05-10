package com.notebook.lumen.notification.preference.application;

import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.preference.domain.UserWorkspaceNotificationPreference;
import com.notebook.lumen.notification.preference.infrastructure.UserWorkspaceNotificationPreferenceRepository;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationPreferenceResolver {
  private final NotificationPreferenceService globalPreferences;
  private final UserWorkspaceNotificationPreferenceRepository workspaceRepository;
  private final NotificationProperties properties;

  public NotificationPreferenceResolver(
      NotificationPreferenceService globalPreferences,
      UserWorkspaceNotificationPreferenceRepository workspaceRepository,
      NotificationProperties properties) {
    this.globalPreferences = globalPreferences;
    this.workspaceRepository = workspaceRepository;
    this.properties = properties;
  }

  /**
   * Effective channel enablement combining global defaults and per-workspace overrides. Mandatory
   * security types are always enabled.
   */
  public boolean isChannelEnabled(
      UUID userId, UUID workspaceId, UserNotificationType type, NotificationChannel channel) {
    if (NotificationWorkspacePreferenceRules.isMandatorySecurityType(type)) {
      return true;
    }
    if (userId == null) {
      return true;
    }
    if (!NotificationWorkspacePreferenceRules.supportsWorkspaceOverride(type)
        || workspaceId == null
        || properties.workspace() == null
        || !properties.workspace().preferencesEnabled()) {
      return globalPreferences.isEnabled(userId, type, channel);
    }
    return workspaceRepository
        .findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(userId, workspaceId, type, channel)
        .map(UserWorkspaceNotificationPreference::isEnabled)
        .orElseGet(() -> globalPreferences.isEnabled(userId, type, channel));
  }

  public boolean isEmailGloballyEnabled(UUID userId, UserNotificationType type) {
    if (NotificationWorkspacePreferenceRules.isMandatorySecurityType(type)) {
      return true;
    }
    if (userId == null) {
      return true;
    }
    return globalPreferences.isEnabled(userId, type, NotificationChannel.EMAIL);
  }

  /**
   * True when email is enabled in global preferences but disabled by an explicit workspace override
   * row.
   */
  public boolean isEmailDisabledOnlyByWorkspace(
      UUID userId, UUID workspaceId, UserNotificationType type) {
    if (!isEmailGloballyEnabled(userId, type)) {
      return false;
    }
    if (workspaceId == null
        || properties.workspace() == null
        || !properties.workspace().preferencesEnabled()
        || !NotificationWorkspacePreferenceRules.supportsWorkspaceOverride(type)) {
      return false;
    }
    return workspaceRepository
        .findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
            userId, workspaceId, type, NotificationChannel.EMAIL)
        .map(row -> !row.isEnabled())
        .orElse(false);
  }
}
