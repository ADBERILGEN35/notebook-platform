package com.notebook.lumen.notification.policy.application;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceChannelPolicyState;
import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceNotificationPoliciesResponse;
import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceNotificationPolicyPatchItem;
import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceNotificationPolicyPatchRequest;
import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceNotificationPolicyRow;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicy;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicyMode;
import com.notebook.lumen.notification.policy.infrastructure.WorkspaceNotificationPolicyRepository;
import com.notebook.lumen.notification.preference.application.NotificationPreferenceService;
import com.notebook.lumen.notification.preference.application.NotificationWorkspacePreferenceRules;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
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
public class WorkspaceNotificationPolicyService {

  private final WorkspaceNotificationPolicyRepository policyRepository;
  private final WorkspaceMembershipClient workspaceMembershipClient;
  private final NotificationProperties properties;
  private final AuditService auditService;

  public WorkspaceNotificationPolicyService(
      WorkspaceNotificationPolicyRepository policyRepository,
      WorkspaceMembershipClient workspaceMembershipClient,
      NotificationProperties properties,
      AuditService auditService) {
    this.policyRepository = policyRepository;
    this.workspaceMembershipClient = workspaceMembershipClient;
    this.properties = properties;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public WorkspaceNotificationPoliciesResponse get(UUID actorUserId, UUID workspaceId) {
    ensurePoliciesEnabled();
    var membership = workspaceMembershipClient.requireWorkspaceMemberForPoliciesRead(actorUserId, workspaceId);
    boolean canManage = WorkspaceRoleRules.isOwnerOrAdmin(membership.role());
    List<WorkspaceNotificationPolicy> stored = policyRepository.findByWorkspaceId(workspaceId);
    Map<String, WorkspaceNotificationPolicy> index = indexPolicies(stored);
    List<WorkspaceNotificationPolicyRow> rows = new ArrayList<>();
    for (UserNotificationType type : NotificationWorkspacePreferenceRules.overridableTypes()) {
      EnumMap<NotificationChannel, WorkspaceChannelPolicyState> channels =
          new EnumMap<>(NotificationChannel.class);
      for (NotificationChannel channel : NotificationChannel.values()) {
        WorkspaceNotificationPolicy row = index.get(policyKey(type, channel));
        WorkspaceNotificationPolicyMode mode =
            row == null ? WorkspaceNotificationPolicyMode.USER_CONTROLLED : row.getPolicyMode();
        String reason = row == null ? null : row.getReason();
        channels.put(
            channel,
            new WorkspaceChannelPolicyState(mode, reason, canManage));
      }
      rows.add(
          new WorkspaceNotificationPolicyRow(
              type, NotificationPreferenceService.labelFor(type), Map.copyOf(channels)));
    }
    return new WorkspaceNotificationPoliciesResponse(workspaceId, canManage, rows);
  }

  @Transactional
  public WorkspaceNotificationPoliciesResponse patch(
      UUID actorUserId, UUID workspaceId, WorkspaceNotificationPolicyPatchRequest request) {
    ensurePoliciesEnabled();
    try {
      workspaceMembershipClient.requireWorkspaceOwnerOrAdmin(actorUserId, workspaceId);
    } catch (NotificationException ex) {
      if (ex.getStatus() == HttpStatus.FORBIDDEN) {
        auditService.record(
            "WORKSPACE_NOTIFICATION_POLICY_UPDATE_DENIED",
            "WORKSPACE_NOTIFICATION_POLICY",
            workspaceId,
            Map.of(
                "actorUserId",
                actorUserId.toString(),
                "workspaceId",
                workspaceId.toString(),
                "updateCount",
                request.updates().size(),
                "reasonPresent",
                false));
      }
      throw ex;
    }
    Instant now = Instant.now();
    for (WorkspaceNotificationPolicyPatchItem item : request.updates()) {
      validatePatchItem(item);
      if (item.policyMode() == WorkspaceNotificationPolicyMode.USER_CONTROLLED) {
        policyRepository
            .findByWorkspaceIdAndNotificationTypeAndChannel(
                workspaceId, item.notificationType(), item.channel())
            .ifPresent(policyRepository::delete);
        continue;
      }
      var existing =
          policyRepository.findByWorkspaceIdAndNotificationTypeAndChannel(
              workspaceId, item.notificationType(), item.channel());
      if (existing.isPresent()) {
        WorkspaceNotificationPolicy p = existing.get();
        p.updatePolicy(item.policyMode(), normalizeReason(item.reason()), actorUserId, now);
      } else {
        policyRepository.save(
            new WorkspaceNotificationPolicy(
                UUID.randomUUID(),
                workspaceId,
                item.notificationType(),
                item.channel(),
                item.policyMode(),
                normalizeReason(item.reason()),
                actorUserId,
                actorUserId,
                now,
                now));
      }
    }
    auditService.record(
        "WORKSPACE_NOTIFICATION_POLICY_UPDATED",
        "WORKSPACE_NOTIFICATION_POLICY",
        workspaceId,
        Map.of(
            "actorUserId",
            actorUserId.toString(),
            "workspaceId",
            workspaceId.toString(),
            "updateCount",
            request.updates().size()));
    return get(actorUserId, workspaceId);
  }

  @Transactional
  public WorkspaceNotificationPoliciesResponse reset(UUID actorUserId, UUID workspaceId) {
    ensurePoliciesEnabled();
    try {
      workspaceMembershipClient.requireWorkspaceOwnerOrAdmin(actorUserId, workspaceId);
    } catch (NotificationException ex) {
      if (ex.getStatus() == HttpStatus.FORBIDDEN) {
        auditService.record(
            "WORKSPACE_NOTIFICATION_POLICY_UPDATE_DENIED",
            "WORKSPACE_NOTIFICATION_POLICY",
            workspaceId,
            Map.of(
                "actorUserId",
                actorUserId.toString(),
                "workspaceId",
                workspaceId.toString(),
                "updateCount",
                0,
                "reasonPresent",
                false));
      }
      throw ex;
    }
    policyRepository.deleteByWorkspaceId(workspaceId);
    auditService.record(
        "WORKSPACE_NOTIFICATION_POLICY_RESET",
        "WORKSPACE_NOTIFICATION_POLICY",
        workspaceId,
        Map.of("actorUserId", actorUserId.toString(), "workspaceId", workspaceId.toString()));
    return get(actorUserId, workspaceId);
  }

  private void validatePatchItem(WorkspaceNotificationPolicyPatchItem item) {
    if (!NotificationWorkspacePreferenceRules.supportsWorkspaceOverride(item.notificationType())) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "INVALID_WORKSPACE_NOTIFICATION_POLICY_REQUEST",
          "Notification type is not eligible for workspace notification policies");
    }
    if (NotificationWorkspacePreferenceRules.isMandatorySecurityType(item.notificationType())
        && item.policyMode() == WorkspaceNotificationPolicyMode.FORCE_DISABLED) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "WORKSPACE_NOTIFICATION_POLICY_MANDATORY_TYPE",
          "Mandatory security notifications cannot be force-disabled by workspace policy");
    }
    boolean reasonRequired =
        properties.workspace() != null && properties.workspace().policyReasonRequiredForForce();
    if (reasonRequired
        && (item.policyMode() == WorkspaceNotificationPolicyMode.FORCE_ENABLED
            || item.policyMode() == WorkspaceNotificationPolicyMode.FORCE_DISABLED)) {
      if (item.reason() == null || item.reason().isBlank()) {
        throw new NotificationException(
            HttpStatus.BAD_REQUEST,
            "WORKSPACE_NOTIFICATION_POLICY_REASON_REQUIRED",
            "A reason is required for force-enabled or force-disabled workspace policies");
      }
    }
  }

  private String normalizeReason(String reason) {
    if (reason == null) {
      return null;
    }
    String t = reason.trim();
    return t.isEmpty() ? null : t;
  }

  private void ensurePoliciesEnabled() {
    if (properties.workspace() == null || !properties.workspace().policiesEnabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND,
          "WORKSPACE_NOTIFICATION_POLICIES_DISABLED",
          "Workspace notification policies are disabled");
    }
  }

  private static Map<String, WorkspaceNotificationPolicy> indexPolicies(
      List<WorkspaceNotificationPolicy> stored) {
    Map<String, WorkspaceNotificationPolicy> map = new HashMap<>();
    for (WorkspaceNotificationPolicy p : stored) {
      map.put(policyKey(p.getNotificationType(), p.getChannel()), p);
    }
    return map;
  }

  static String policyKey(UserNotificationType type, NotificationChannel channel) {
    return type.name() + "::" + channel.name();
  }
}
