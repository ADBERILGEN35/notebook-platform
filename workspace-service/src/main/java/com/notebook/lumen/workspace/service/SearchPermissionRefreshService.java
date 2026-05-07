package com.notebook.lumen.workspace.service;

import com.notebook.lumen.workspace.audit.AuditService;
import com.notebook.lumen.workspace.client.SearchPermissionRefreshClient;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SearchPermissionRefreshService {
  private static final Logger log = LoggerFactory.getLogger(SearchPermissionRefreshService.class);

  private final SearchPermissionRefreshClient client;
  private final WorkspaceProperties properties;
  private final AuditService auditService;

  public SearchPermissionRefreshService(
      SearchPermissionRefreshClient client, WorkspaceProperties properties, AuditService auditService) {
    this.client = client;
    this.properties = properties;
    this.auditService = auditService;
  }

  public void triggerNotebookRefresh(UUID actorUserId, UUID workspaceId, UUID notebookId) {
    WorkspaceProperties.Search search = properties.search();
    if (search == null || !search.permissionRefreshEnabled()) {
      return;
    }

    auditService.record(
        "SEARCH_PERMISSION_REFRESH_REQUESTED",
        actorUserId,
        workspaceId,
        "NOTEBOOK",
        notebookId,
        Map.of("notebookId", notebookId.toString()));

    try {
      client.refreshNotebookPermissionSnapshot(notebookId);
    } catch (RuntimeException ex) {
      log.warn(
          "Search permission refresh failed workspaceId={} notebookId={}",
          workspaceId,
          notebookId,
          ex);
      auditService.record(
          "SEARCH_PERMISSION_REFRESH_FAILED",
          actorUserId,
          workspaceId,
          "NOTEBOOK",
          notebookId,
          Map.of("notebookId", notebookId.toString(), "reason", "search-refresh-client-failure"));
      // Do NOT fail the workspace mutation.
    }
  }
}

