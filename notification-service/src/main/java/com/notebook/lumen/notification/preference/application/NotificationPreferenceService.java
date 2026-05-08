package com.notebook.lumen.notification.preference.application;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.preference.domain.UserNotificationPreference;
import com.notebook.lumen.notification.preference.infrastructure.UserNotificationPreferenceRepository;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {
  private final UserNotificationPreferenceRepository repository;
  private final AuditService auditService;

  public NotificationPreferenceService(
      UserNotificationPreferenceRepository repository, AuditService auditService) {
    this.repository = repository;
    this.auditService = auditService;
  }

  @Transactional
  public List<UserNotificationPreference> list(UUID userId) {
    ensureDefaults(userId);
    return repository.findByUserId(userId);
  }

  @Transactional
  public List<UserNotificationPreference> update(UUID userId, List<PreferenceUpdate> updates) {
    ensureDefaults(userId);
    Instant now = Instant.now();
    for (PreferenceUpdate update : updates) {
      UserNotificationPreference pref =
          repository
              .findByUserIdAndNotificationTypeAndChannel(
                  userId, update.notificationType(), update.channel())
              .orElseThrow(
                  () ->
                      new NotificationException(
                          HttpStatus.NOT_FOUND,
                          "NOTIFICATION_PREFERENCE_NOT_FOUND",
                          "Notification preference not found"));
      if (pref.isMandatory() && !update.enabled()) {
        throw new NotificationException(
            HttpStatus.BAD_REQUEST,
            "MANDATORY_NOTIFICATION_PREFERENCE",
            "Mandatory notification preferences cannot be disabled");
      }
      pref.updateEnabled(update.enabled(), now);
    }
    auditService.record(
        "USER_NOTIFICATION_PREFERENCES_UPDATED",
        "USER_NOTIFICATION_PREFERENCE",
        userId,
        Map.of("userId", userId.toString(), "updatedCount", updates.size()));
    return repository.findByUserId(userId);
  }

  @Transactional
  public boolean isEnabled(UUID userId, UserNotificationType type, NotificationChannel channel) {
    ensureDefaults(userId);
    return repository
        .findByUserIdAndNotificationTypeAndChannel(userId, type, channel)
        .map(pref -> pref.isMandatory() || pref.isEnabled())
        .orElse(true);
  }

  private void ensureDefaults(UUID userId) {
    List<UserNotificationPreference> existing = repository.findByUserId(userId);
    if (!existing.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    for (Map.Entry<UserNotificationType, Map<NotificationChannel, PreferenceDefaults>> entry :
        defaultMatrix().entrySet()) {
      for (Map.Entry<NotificationChannel, PreferenceDefaults> channelEntry : entry.getValue().entrySet()) {
        PreferenceDefaults defaults = channelEntry.getValue();
        repository.save(
            new UserNotificationPreference(
                UUID.randomUUID(),
                userId,
                entry.getKey(),
                channelEntry.getKey(),
                defaults.enabled(),
                defaults.mandatory(),
                now));
      }
    }
  }

  public static String labelFor(UserNotificationType type) {
    return switch (type) {
      case SECURITY_SESSIONS_REVOKED -> "Security sessions revoked";
      case WORKSPACE_INVITATION_RECEIVED -> "Workspace invitations";
      case COMMENT_ADDED -> "Comments";
      case NOTE_VERSION_RESTORED -> "Note versions";
      case SYSTEM_NOTICE -> "System notices";
    };
  }

  public static String descriptionFor(UserNotificationType type) {
    return switch (type) {
      case SECURITY_SESSIONS_REVOKED -> "Required account security alerts.";
      case WORKSPACE_INVITATION_RECEIVED -> "When you are invited to a workspace.";
      case COMMENT_ADDED -> "When someone comments on your notes.";
      case NOTE_VERSION_RESTORED -> "When a note version is restored.";
      case SYSTEM_NOTICE -> "General platform notices.";
    };
  }

  private Map<UserNotificationType, Map<NotificationChannel, PreferenceDefaults>> defaultMatrix() {
    Map<UserNotificationType, Map<NotificationChannel, PreferenceDefaults>> matrix =
        new LinkedHashMap<>();
    matrix.put(
        UserNotificationType.SECURITY_SESSIONS_REVOKED,
        channelDefaults(
            new PreferenceDefaults(true, true), new PreferenceDefaults(true, true)));
    matrix.put(
        UserNotificationType.WORKSPACE_INVITATION_RECEIVED,
        channelDefaults(
            new PreferenceDefaults(true, false), new PreferenceDefaults(true, false)));
    matrix.put(
        UserNotificationType.COMMENT_ADDED,
        channelDefaults(
            new PreferenceDefaults(true, false), new PreferenceDefaults(false, false)));
    matrix.put(
        UserNotificationType.NOTE_VERSION_RESTORED,
        channelDefaults(
            new PreferenceDefaults(true, false), new PreferenceDefaults(false, false)));
    matrix.put(
        UserNotificationType.SYSTEM_NOTICE,
        channelDefaults(
            new PreferenceDefaults(true, false), new PreferenceDefaults(false, false)));
    return matrix;
  }

  private Map<NotificationChannel, PreferenceDefaults> channelDefaults(
      PreferenceDefaults inApp, PreferenceDefaults email) {
    Map<NotificationChannel, PreferenceDefaults> map = new EnumMap<>(NotificationChannel.class);
    map.put(NotificationChannel.IN_APP, inApp);
    map.put(NotificationChannel.EMAIL, email);
    return map;
  }

  public record PreferenceUpdate(
      UserNotificationType notificationType, NotificationChannel channel, boolean enabled) {}

  private record PreferenceDefaults(boolean enabled, boolean mandatory) {}
}
