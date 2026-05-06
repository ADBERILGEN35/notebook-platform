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

  record NotebookPermissionResponse(
      UUID workspaceId,
      UUID notebookId,
      String role,
      boolean canRead,
      boolean canEdit,
      boolean canComment,
      boolean canManage) {}
}
