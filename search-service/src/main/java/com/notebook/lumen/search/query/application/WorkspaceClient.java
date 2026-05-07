package com.notebook.lumen.search.query.application;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface WorkspaceClient {
  @GetExchange("/internal/notebooks/{notebookId}/permissions")
  NotebookPermissionResponse notebookPermissions(
      @PathVariable UUID notebookId, @RequestParam UUID userId);

  @GetExchange("/internal/workspaces/{workspaceId}/permissions")
  WorkspaceMembershipResponse workspaceMembership(
      @PathVariable UUID workspaceId, @RequestParam UUID userId);

  @GetExchange("/internal/notebooks/{notebookId}/search-permission-snapshot")
  SearchPermissionSnapshotResponse searchPermissionSnapshot(@PathVariable UUID notebookId);

  record NotebookPermissionResponse(
      UUID workspaceId,
      UUID notebookId,
      String role,
      boolean canRead,
      boolean canEdit,
      boolean canComment,
      boolean canManage) {}

  record WorkspaceMembershipResponse(UUID workspaceId, UUID userId, boolean isMember, String role) {}

  record SearchPermissionSnapshotResponse(
      UUID workspaceId,
      UUID notebookId,
      String visibilityMode,
      boolean workspaceReadable,
      boolean restricted,
      Integer permissionVersion,
      java.time.Instant updatedAt) {}
}
