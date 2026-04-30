package com.notebook.lumen.workspace.shared;

import com.notebook.lumen.workspace.shared.exception.MissingUserContextException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserContextResolver {

  public UserContext require(HttpServletRequest request) {
    String userIdHeader = request.getHeader("X-User-Id");
    if (userIdHeader == null || userIdHeader.isBlank()) {
      throw new MissingUserContextException("X-User-Id header is required");
    }
    try {
      UUID workspaceId = null;
      String workspaceIdHeader = request.getHeader("X-Workspace-Id");
      if (workspaceIdHeader != null && !workspaceIdHeader.isBlank()) {
        workspaceId = UUID.fromString(workspaceIdHeader);
      }
      return new UserContext(
          UUID.fromString(userIdHeader), request.getHeader("X-User-Email"), workspaceId);
    } catch (IllegalArgumentException e) {
      throw new MissingUserContextException(
          "X-User-Id or X-Workspace-Id header must be a valid UUID");
    }
  }
}
