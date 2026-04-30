package com.notebook.lumen.workspace.dto;

import java.util.UUID;

public final class InternalResponses {
  private InternalResponses() {}

  public record NotebookPermissionResponse(
      UUID workspaceId,
      UUID notebookId,
      String role,
      boolean canRead,
      boolean canEdit,
      boolean canComment,
      boolean canManage) {}

  public record TagExistsResponse(UUID workspaceId, UUID tagId, String scope, boolean exists) {}
}
