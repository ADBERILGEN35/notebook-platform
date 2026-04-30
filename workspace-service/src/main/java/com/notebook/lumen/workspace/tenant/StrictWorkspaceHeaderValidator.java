package com.notebook.lumen.workspace.tenant;

import com.notebook.lumen.workspace.shared.UserContext;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StrictWorkspaceHeaderValidator {
  private final boolean strictWorkspaceHeader;

  public StrictWorkspaceHeaderValidator(
      @Value("${app.rls.strict-workspace-header:false}") boolean strictWorkspaceHeader) {
    this.strictWorkspaceHeader = strictWorkspaceHeader;
  }

  public void validateAggregateRequest(UserContext user, UUID resolvedWorkspaceId) {
    if (strictWorkspaceHeader && user.workspaceId() == null) {
      throw Exceptions.badRequest(
          "MISSING_WORKSPACE_CONTEXT",
          "X-Workspace-Id header is required for tenant-scoped aggregate requests");
    }
    if (user.workspaceId() != null && !user.workspaceId().equals(resolvedWorkspaceId)) {
      throw Exceptions.badRequest(
          "INVALID_WORKSPACE_CONTEXT", "X-Workspace-Id conflicts with resolved workspace");
    }
  }

  public void validateIfPresent(UserContext user, UUID resolvedWorkspaceId) {
    if (user.workspaceId() != null && !user.workspaceId().equals(resolvedWorkspaceId)) {
      throw Exceptions.badRequest(
          "INVALID_WORKSPACE_CONTEXT", "X-Workspace-Id conflicts with resolved workspace");
    }
  }
}
