package com.notebook.lumen.content.shared;

import com.notebook.lumen.content.shared.exception.ContentException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UserContextResolver {
  public UserContext require(HttpServletRequest request) {
    String rawUserId = request.getHeader("X-User-Id");
    if (rawUserId == null || rawUserId.isBlank()) {
      throw new ContentException(
          HttpStatus.UNAUTHORIZED, "MISSING_USER_CONTEXT", "X-User-Id header is required");
    }
    try {
      UUID workspaceId = null;
      String rawWorkspaceId = request.getHeader("X-Workspace-Id");
      if (rawWorkspaceId != null && !rawWorkspaceId.isBlank()) {
        workspaceId = UUID.fromString(rawWorkspaceId);
      }
      return new UserContext(UUID.fromString(rawUserId), workspaceId);
    } catch (IllegalArgumentException e) {
      throw new ContentException(
          HttpStatus.BAD_REQUEST,
          "INVALID_WORKSPACE_CONTEXT",
          "User or workspace header is not a valid UUID");
    }
  }
}
