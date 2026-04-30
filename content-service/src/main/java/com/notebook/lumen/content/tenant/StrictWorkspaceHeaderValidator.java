package com.notebook.lumen.content.tenant;

import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.exception.ContentException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
      throw new ContentException(
          HttpStatus.BAD_REQUEST,
          "MISSING_WORKSPACE_CONTEXT",
          "X-Workspace-Id header is required for tenant-scoped aggregate requests");
    }
    if (user.workspaceId() != null && !user.workspaceId().equals(resolvedWorkspaceId)) {
      throw new ContentException(
          HttpStatus.BAD_REQUEST,
          "INVALID_WORKSPACE_CONTEXT",
          "X-Workspace-Id conflicts with resolved workspace");
    }
  }

  public void validateIfPresent(UserContext user, UUID resolvedWorkspaceId) {
    if (user.workspaceId() != null && !user.workspaceId().equals(resolvedWorkspaceId)) {
      throw new ContentException(
          HttpStatus.BAD_REQUEST,
          "INVALID_WORKSPACE_CONTEXT",
          "X-Workspace-Id conflicts with resolved workspace");
    }
  }
}
