package com.notebook.lumen.notification.preference.application;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsEventKind;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicy;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicyMode;
import com.notebook.lumen.notification.policy.infrastructure.WorkspaceNotificationPolicyRepository;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.preference.domain.UserWorkspaceNotificationPreference;
import com.notebook.lumen.notification.preference.infrastructure.UserWorkspaceNotificationPreferenceRepository;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationPreferenceResolver {
  private final NotificationPreferenceService globalPreferences;
  private final UserWorkspaceNotificationPreferenceRepository workspaceRepository;
  private final WorkspaceNotificationPolicyRepository policyRepository;
  private final NotificationProperties properties;

  public NotificationPreferenceResolver(
      NotificationPreferenceService globalPreferences,
      UserWorkspaceNotificationPreferenceRepository workspaceRepository,
      WorkspaceNotificationPolicyRepository policyRepository,
      NotificationProperties properties) {
    this.globalPreferences = globalPreferences;
    this.workspaceRepository = workspaceRepository;
    this.policyRepository = policyRepository;
    this.properties = properties;
  }

  /**
   * Effective channel enablement: mandatory security → workspace admin policy → per-workspace user
   * override → global → default.
   */
  public boolean isChannelEnabled(
      UUID userId, UUID workspaceId, UserNotificationType type, NotificationChannel channel) {
    if (NotificationWorkspacePreferenceRules.isMandatorySecurityType(type)) {
      return true;
    }
    if (userId == null) {
      return true;
    }
    boolean base = resolveBaseChannelEnabled(userId, workspaceId, type, channel);
    return applyWorkspacePolicy(workspaceId, type, channel, base);
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
   * When {@link #isChannelEnabled} is false, classifies an aggregate-safe analytics reason (no
   * workspace identifiers).
   */
  public NotificationAnalyticsEventKind classifyChannelDisabledAnalyticsReason(
      UUID userId, UUID workspaceId, UserNotificationType type, NotificationChannel channel) {
    if (userId == null) {
      return NotificationAnalyticsEventKind.SKIPPED_PREFERENCE;
    }
    boolean base = resolveBaseChannelEnabled(userId, workspaceId, type, channel);
    Optional<WorkspaceNotificationPolicy> policy = lookupPolicy(workspaceId, type, channel);
    if (policy.isPresent()
        && policy.get().getPolicyMode() == WorkspaceNotificationPolicyMode.FORCE_DISABLED
        && base) {
      return NotificationAnalyticsEventKind.SKIPPED_WORKSPACE_ADMIN_POLICY;
    }
    if (workspaceOverrideDisablesChannel(userId, workspaceId, type, channel)) {
      return NotificationAnalyticsEventKind.SKIPPED_WORKSPACE_PREFERENCE;
    }
    return NotificationAnalyticsEventKind.SKIPPED_PREFERENCE;
  }

  /**
   * True when email is enabled in global preferences but disabled by an explicit workspace override
   * row (policy layer ignored).
   */
  public boolean isEmailDisabledOnlyByWorkspace(
      UUID userId, UUID workspaceId, UserNotificationType type) {
    return workspaceOverrideDisablesChannel(userId, workspaceId, type, NotificationChannel.EMAIL);
  }

  private boolean resolveBaseChannelEnabled(
      UUID userId, UUID workspaceId, UserNotificationType type, NotificationChannel channel) {
    var w = properties.workspace();
    if (!NotificationWorkspacePreferenceRules.supportsWorkspaceOverride(type)
        || workspaceId == null
        || w == null
        || !w.preferencesEnabled()) {
      return globalPreferences.isEnabled(userId, type, channel);
    }
    return workspaceRepository
        .findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(userId, workspaceId, type, channel)
        .map(UserWorkspaceNotificationPreference::isEnabled)
        .orElseGet(() -> globalPreferences.isEnabled(userId, type, channel));
  }

  private boolean applyWorkspacePolicy(
      UUID workspaceId,
      UserNotificationType type,
      NotificationChannel channel,
      boolean basePreference) {
    Optional<WorkspaceNotificationPolicy> policy = lookupPolicy(workspaceId, type, channel);
    if (policy.isEmpty()) {
      return basePreference;
    }
    return switch (policy.get().getPolicyMode()) {
      case USER_CONTROLLED -> basePreference;
      case FORCE_ENABLED -> true;
      case FORCE_DISABLED -> false;
    };
  }

  private Optional<WorkspaceNotificationPolicy> lookupPolicy(
      UUID workspaceId, UserNotificationType type, NotificationChannel channel) {
    var w = properties.workspace();
    if (w == null
        || !w.policiesEnabled()
        || workspaceId == null
        || !NotificationWorkspacePreferenceRules.supportsWorkspaceOverride(type)) {
      return Optional.empty();
    }
    return policyRepository.findByWorkspaceIdAndNotificationTypeAndChannel(
        workspaceId, type, channel);
  }

  private boolean workspaceOverrideDisablesChannel(
      UUID userId, UUID workspaceId, UserNotificationType type, NotificationChannel channel) {
    var w = properties.workspace();
    if (workspaceId == null
        || w == null
        || !w.preferencesEnabled()
        || !NotificationWorkspacePreferenceRules.supportsWorkspaceOverride(type)) {
      return false;
    }
    if (!globalPreferences.isEnabled(userId, type, channel)) {
      return false;
    }
    return workspaceRepository
        .findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(userId, workspaceId, type, channel)
        .map(row -> !row.isEnabled())
        .orElse(false);
  }
}
