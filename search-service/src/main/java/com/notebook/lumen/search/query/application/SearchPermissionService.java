package com.notebook.lumen.search.query.application;

import com.notebook.lumen.search.shared.exception.SearchException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SearchPermissionService {
  private static final Logger log = LoggerFactory.getLogger(SearchPermissionService.class);

  private final WorkspaceClient workspaceClient;

  public SearchPermissionService(WorkspaceClient workspaceClient) {
    this.workspaceClient = workspaceClient;
  }

  public boolean canRead(UUID userId, UUID workspaceId, UUID notebookId) {
    if (notebookId == null) {
      return true;
    }
    try {
      WorkspaceClient.NotebookPermissionResponse permission =
          workspaceClient.notebookPermissions(notebookId, userId);
      return workspaceId.equals(permission.workspaceId()) && permission.canRead();
    } catch (Exception e) {
      log.error(
          "Workspace permission check failed userId={} workspaceId={} notebookId={}",
          userId,
          workspaceId,
          notebookId,
          e);
      throw new SearchException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "WORKSPACE_PERMISSION_UNAVAILABLE",
          "Workspace permission unavailable");
    }
  }
}
