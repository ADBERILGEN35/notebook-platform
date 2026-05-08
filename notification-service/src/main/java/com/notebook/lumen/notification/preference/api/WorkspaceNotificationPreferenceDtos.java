package com.notebook.lumen.notification.preference.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkspaceNotificationPreferenceDtos {
  private WorkspaceNotificationPreferenceDtos() {}

  public record WorkspaceNotificationPreferencesResponse(
      UUID workspaceId, List<WorkspacePreferenceRow> preferences) {}

  public record WorkspacePreferenceRow(
      UserNotificationType notificationType,
      String label,
      String description,
      Map<NotificationChannel, WorkspaceChannelState> channels) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record WorkspaceChannelState(
      Boolean enabled,
      boolean inherited,
      boolean effectiveEnabled,
      boolean mandatory) {}

  public record WorkspaceNotificationPreferencePatchRequest(
      @NotNull @NotEmpty List<@Valid WorkspacePreferencePatchItem> updates) {}

  public record WorkspacePreferencePatchItem(
      @NotNull UserNotificationType notificationType,
      @NotNull NotificationChannel channel,
      boolean inheritGlobal,
      Boolean enabled) {}
}
