package com.notebook.lumen.notification.preference.application;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicy;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicyMode;
import com.notebook.lumen.notification.policy.infrastructure.WorkspaceNotificationPolicyRepository;
import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspaceChannelState;
import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspaceNotificationPreferencePatchRequest;
import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspaceNotificationPreferencesResponse;
import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspacePolicyState;
import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspacePreferencePatchItem;
import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspacePreferenceRow;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.preference.domain.UserNotificationPreference;
import com.notebook.lumen.notification.preference.domain.UserWorkspaceNotificationPreference;
import com.notebook.lumen.notification.preference.infrastructure.UserWorkspaceNotificationPreferenceRepository;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import com.notebook.lumen.notification.workspace.WorkspaceMembershipClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceNotificationPreferenceService {

  private final NotificationPreferenceService globalPreferenceService;
  private final NotificationPreferenceResolver preferenceResolver;
  private final UserWorkspaceNotificationPreferenceRepository workspaceRepository;
  private final WorkspaceMembershipClient workspaceMembershipClient;
  private final WorkspaceNotificationPolicyRepository policyRepository;
  private final NotificationProperties properties;
  private final AuditService auditService;

  public WorkspaceNotificationPreferenceService(
      NotificationPreferenceService globalPreferenceService,
      NotificationPreferenceResolver preferenceResolver,
      UserWorkspaceNotificationPreferenceRepository workspaceRepository,
      WorkspaceMembershipClient workspaceMembershipClient,
      WorkspaceNotificationPolicyRepository policyRepository,
      NotificationProperties properties,
      AuditService auditService) {
    this.globalPreferenceService = globalPreferenceService;
    this.preferenceResolver = preferenceResolver;
    this.workspaceRepository = workspaceRepository;
    this.workspaceMembershipClient = workspaceMembershipClient;
    this.policyRepository = policyRepository;
    this.properties = properties;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public WorkspaceNotificationPreferencesResponse get(UUID userId, UUID workspaceId) {
    ensureFeatureEnabled();
    workspaceMembershipClient.requireWorkspaceMember(userId, workspaceId);
    List<UserNotificationPreference> globals = globalPreferenceService.list(userId);
    Map<UserNotificationType, Map<NotificationChannel, UserNotificationPreference>> globalMap =
        indexGlobals(globals);
    List<UserWorkspaceNotificationPreference> overrides =
        workspaceRepository.findByUserIdAndWorkspaceId(userId, workspaceId);
    Map<String, UserWorkspaceNotificationPreference> overrideIndex = indexOverrides(overrides);
    Map<String, WorkspaceNotificationPolicy> policyIndex = indexWorkspacePolicies(workspaceId);

    List<WorkspacePreferenceRow> rows = new ArrayList<>();
    for (UserNotificationType type : NotificationWorkspacePreferenceRules.overridableTypes()) {
      EnumMap<NotificationChannel, WorkspaceChannelState> channels = new EnumMap<>(NotificationChannel.class);
      for (NotificationChannel channel : NotificationChannel.values()) {
        UserNotificationPreference globalPref = globalMap.getOrDefault(type, Map.of()).get(channel);
        if (globalPref == null) {
          continue;
        }
        String key = overrideKey(type, channel);
        UserWorkspaceNotificationPreference ov = overrideIndex.get(key);
        WorkspaceNotificationPolicy pol = policyIndex.get(key);
        WorkspaceNotificationPolicyMode polMode =
            pol == null ? WorkspaceNotificationPolicyMode.USER_CONTROLLED : pol.getPolicyMode();
        boolean lockedByPolicy =
            polMode == WorkspaceNotificationPolicyMode.FORCE_ENABLED
                || polMode == WorkspaceNotificationPolicyMode.FORCE_DISABLED;
        WorkspacePolicyState workspacePolicyState =
            pol != null && polMode != WorkspaceNotificationPolicyMode.USER_CONTROLLED
                ? new WorkspacePolicyState(polMode.name(), pol.getReason())
                : null;
        boolean inherited = ov == null;
        boolean effective =
            preferenceResolver.isChannelEnabled(userId, workspaceId, type, channel);
        boolean mandatory = globalPref.isMandatory();
        Boolean enabledForUi =
            inherited ? globalPref.isEnabled() : ov.isEnabled();
        channels.put(
            channel,
            new WorkspaceChannelState(
                enabledForUi,
                inherited,
                effective,
                mandatory,
                workspacePolicyState,
                lockedByPolicy));
      }
      rows.add(
          new WorkspacePreferenceRow(
              type,
              NotificationPreferenceService.labelFor(type),
              workspaceDescriptionFor(type),
              channels));
    }
    return new WorkspaceNotificationPreferencesResponse(workspaceId, rows);
  }

  @Transactional
  public WorkspaceNotificationPreferencesResponse patch(
      UUID userId, UUID workspaceId, WorkspaceNotificationPreferencePatchRequest request) {
    ensureFeatureEnabled();
    workspaceMembershipClient.requireWorkspaceMember(userId, workspaceId);
    Instant now = Instant.now();
    for (WorkspacePreferencePatchItem item : request.updates()) {
      validatePatchItem(item);
      UserNotificationPreference global =
          globalPreferenceService.list(userId).stream()
              .filter(
                  p ->
                      p.getNotificationType() == item.notificationType()
                          && p.getChannel() == item.channel())
              .findFirst()
              .orElseThrow(
                  () ->
                      new NotificationException(
                          HttpStatus.NOT_FOUND,
                          "WORKSPACE_NOTIFICATION_PREFERENCE_NOT_FOUND",
                          "Notification preference not found"));
      if (global.isMandatory() && Boolean.FALSE.equals(item.enabled()) && !item.inheritGlobal()) {
        throw new NotificationException(
            HttpStatus.BAD_REQUEST,
            "MANDATORY_NOTIFICATION_PREFERENCE",
            "Mandatory notification preferences cannot be disabled");
      }
      var key =
          workspaceRepository.findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
              userId, workspaceId, item.notificationType(), item.channel());
      if (item.inheritGlobal()) {
        key.ifPresent(workspaceRepository::delete);
      } else {
        if (item.enabled() == null) {
          throw new NotificationException(
              HttpStatus.BAD_REQUEST,
              "INVALID_WORKSPACE_NOTIFICATION_PREFERENCE_REQUEST",
              "enabled is required when inheritGlobal is false");
        }
        if (key.isPresent()) {
          UserWorkspaceNotificationPreference existing = key.get();
          existing.updateEnabled(item.enabled(), now);
        } else {
          workspaceRepository.save(
              new UserWorkspaceNotificationPreference(
                  UUID.randomUUID(),
                  userId,
                  workspaceId,
                  item.notificationType(),
                  item.channel(),
                  item.enabled(),
                  now));
        }
      }
    }
    auditService.record(
        "USER_WORKSPACE_NOTIFICATION_PREFERENCES_UPDATED",
        "USER_WORKSPACE_NOTIFICATION_PREFERENCE",
        workspaceId,
        Map.of(
            "userId",
            userId.toString(),
            "workspaceId",
            workspaceId.toString(),
            "updatedCount",
            request.updates().size()));
    return get(userId, workspaceId);
  }

  @Transactional
  public WorkspaceNotificationPreferencesResponse reset(UUID userId, UUID workspaceId) {
    ensureFeatureEnabled();
    workspaceMembershipClient.requireWorkspaceMember(userId, workspaceId);
    workspaceRepository.deleteByUserIdAndWorkspaceId(userId, workspaceId);
    auditService.record(
        "USER_WORKSPACE_NOTIFICATION_PREFERENCES_RESET",
        "USER_WORKSPACE_NOTIFICATION_PREFERENCE",
        workspaceId,
        Map.of("userId", userId.toString(), "workspaceId", workspaceId.toString()));
    return get(userId, workspaceId);
  }

  private void validatePatchItem(WorkspacePreferencePatchItem item) {
    if (!NotificationWorkspacePreferenceRules.supportsWorkspaceOverride(item.notificationType())) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "INVALID_WORKSPACE_NOTIFICATION_PREFERENCE_REQUEST",
          "Notification type cannot be overridden per workspace");
    }
    if (NotificationWorkspacePreferenceRules.isMandatorySecurityType(item.notificationType())) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "INVALID_WORKSPACE_NOTIFICATION_PREFERENCE_REQUEST",
          "Security notification preferences cannot be workspace-scoped");
    }
  }

  private void ensureFeatureEnabled() {
    if (properties.workspace() == null || !properties.workspace().preferencesEnabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND,
          "WORKSPACE_NOTIFICATION_PREFERENCES_DISABLED",
          "Workspace notification preferences are disabled");
    }
    if (properties.preferences() == null || !properties.preferences().enabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND,
          "NOTIFICATION_PREFERENCE_NOT_FOUND",
          "Notification preferences are disabled");
    }
  }

  private static Map<UserNotificationType, Map<NotificationChannel, UserNotificationPreference>>
      indexGlobals(List<UserNotificationPreference> globals) {
    Map<UserNotificationType, Map<NotificationChannel, UserNotificationPreference>> map =
        new EnumMap<>(UserNotificationType.class);
    for (UserNotificationPreference p : globals) {
      map.computeIfAbsent(p.getNotificationType(), ignored -> new EnumMap<>(NotificationChannel.class))
          .put(p.getChannel(), p);
    }
    return map;
  }

  private static Map<String, UserWorkspaceNotificationPreference> indexOverrides(
      List<UserWorkspaceNotificationPreference> overrides) {
    Map<String, UserWorkspaceNotificationPreference> map = new java.util.HashMap<>();
    for (UserWorkspaceNotificationPreference o : overrides) {
      map.put(overrideKey(o.getNotificationType(), o.getChannel()), o);
    }
    return map;
  }

  private static String overrideKey(UserNotificationType type, NotificationChannel channel) {
    return type.name() + "::" + channel.name();
  }

  private Map<String, WorkspaceNotificationPolicy> indexWorkspacePolicies(UUID workspaceId) {
    if (properties.workspace() == null || !properties.workspace().policiesEnabled()) {
      return Map.of();
    }
    Map<String, WorkspaceNotificationPolicy> map = new HashMap<>();
    for (WorkspaceNotificationPolicy p : policyRepository.findByWorkspaceId(workspaceId)) {
      map.put(overrideKey(p.getNotificationType(), p.getChannel()), p);
    }
    return map;
  }

  private static String workspaceDescriptionFor(UserNotificationType type) {
    return switch (type) {
      case COMMENT_ADDED -> "When someone comments in this workspace.";
      case NOTE_VERSION_RESTORED -> "When a note version is restored in this workspace.";
      case SYSTEM_NOTICE -> "General notices related to activity in this workspace.";
      case WORKSPACE_INVITATION_RECEIVED -> "When you are invited to a workspace.";
      default -> NotificationPreferenceService.descriptionFor(type);
    };
  }
}
