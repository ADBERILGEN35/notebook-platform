package com.notebook.lumen.notification.preference.api;

import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspaceNotificationPreferencePatchRequest;
import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspaceNotificationPreferencesResponse;
import com.notebook.lumen.notification.preference.application.WorkspaceNotificationPreferenceService;
import com.notebook.lumen.notification.shared.web.UserContextResolver;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification-preferences/workspaces")
public class WorkspaceNotificationPreferenceController {

  private final WorkspaceNotificationPreferenceService service;
  private final UserContextResolver userContextResolver;

  public WorkspaceNotificationPreferenceController(
      WorkspaceNotificationPreferenceService service, UserContextResolver userContextResolver) {
    this.service = service;
    this.userContextResolver = userContextResolver;
  }

  @GetMapping("/{workspaceId}")
  public WorkspaceNotificationPreferencesResponse get(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @PathVariable UUID workspaceId) {
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return service.get(userId, workspaceId);
  }

  @PatchMapping("/{workspaceId}")
  public WorkspaceNotificationPreferencesResponse patch(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody WorkspaceNotificationPreferencePatchRequest request) {
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return service.patch(userId, workspaceId, request);
  }

  @PostMapping("/{workspaceId}/reset")
  public WorkspaceNotificationPreferencesResponse reset(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @PathVariable UUID workspaceId) {
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return service.reset(userId, workspaceId);
  }
}
