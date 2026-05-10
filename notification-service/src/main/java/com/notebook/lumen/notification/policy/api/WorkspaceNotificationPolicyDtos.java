package com.notebook.lumen.notification.policy.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicyMode;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkspaceNotificationPolicyDtos {
  private WorkspaceNotificationPolicyDtos() {}

  public record WorkspaceNotificationPoliciesResponse(
      UUID workspaceId,
      boolean canManagePolicies,
      List<WorkspaceNotificationPolicyRow> policies) {}

  public record WorkspaceNotificationPolicyRow(
      UserNotificationType notificationType,
      String label,
      Map<NotificationChannel, WorkspaceChannelPolicyState> channels) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record WorkspaceChannelPolicyState(
      WorkspaceNotificationPolicyMode policyMode,
      String reason,
      boolean manageable) {}

  public record WorkspaceNotificationPolicyPatchRequest(
      @NotNull @NotEmpty List<@Valid WorkspaceNotificationPolicyPatchItem> updates) {}

  public record WorkspaceNotificationPolicyPatchItem(
      @NotNull UserNotificationType notificationType,
      @NotNull NotificationChannel channel,
      @NotNull WorkspaceNotificationPolicyMode policyMode,
      String reason) {}
}
