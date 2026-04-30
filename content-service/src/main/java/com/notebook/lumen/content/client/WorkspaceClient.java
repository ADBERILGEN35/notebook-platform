package com.notebook.lumen.content.client;

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

  @GetExchange("/internal/workspaces/{workspaceId}/tags/{tagId}/exists")
  TagExistsResponse tagExists(
      @PathVariable UUID workspaceId, @PathVariable UUID tagId, @RequestParam String scope);

  record NotebookPermissionResponse(
      UUID workspaceId,
      UUID notebookId,
      String role,
      boolean canRead,
      boolean canEdit,
      boolean canComment,
      boolean canManage) {}

  record TagExistsResponse(UUID workspaceId, UUID tagId, String scope, boolean exists) {}
}
