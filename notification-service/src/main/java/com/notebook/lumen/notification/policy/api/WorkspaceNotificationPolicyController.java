package com.notebook.lumen.notification.policy.api;

import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceNotificationPoliciesResponse;
import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceNotificationPolicyPatchRequest;
import com.notebook.lumen.notification.policy.application.WorkspaceNotificationPolicyService;
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
@RequestMapping("/notification-policies/workspaces")
public class WorkspaceNotificationPolicyController {

  private final WorkspaceNotificationPolicyService service;
  private final UserContextResolver userContextResolver;

  public WorkspaceNotificationPolicyController(
      WorkspaceNotificationPolicyService service, UserContextResolver userContextResolver) {
    this.service = service;
    this.userContextResolver = userContextResolver;
  }

  @GetMapping("/{workspaceId}")
  public WorkspaceNotificationPoliciesResponse get(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @PathVariable UUID workspaceId) {
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return service.get(userId, workspaceId);
  }

  @PatchMapping("/{workspaceId}")
  public WorkspaceNotificationPoliciesResponse patch(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody WorkspaceNotificationPolicyPatchRequest request) {
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return service.patch(userId, workspaceId, request);
  }

  @PostMapping("/{workspaceId}/reset")
  public WorkspaceNotificationPoliciesResponse reset(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @PathVariable UUID workspaceId) {
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return service.reset(userId, workspaceId);
  }
}
