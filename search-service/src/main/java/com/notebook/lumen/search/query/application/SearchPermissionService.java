package com.notebook.lumen.search.query.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SearchPermissionService {
  private static final Logger log = LoggerFactory.getLogger(SearchPermissionService.class);

  private final WorkspaceClient workspaceClient;
  private final MeterRegistry meterRegistry;

  public SearchPermissionService(WorkspaceClient workspaceClient, MeterRegistry meterRegistry) {
    this.workspaceClient = workspaceClient;
    this.meterRegistry = meterRegistry;
  }

  public SearchPermissionService(WorkspaceClient workspaceClient) {
    this(workspaceClient, new SimpleMeterRegistry());
  }

  public boolean isWorkspaceMember(UUID userId, UUID workspaceId) {
    try {
      WorkspaceClient.WorkspaceMembershipResponse membership =
          workspaceClient.workspaceMembership(workspaceId, userId);
      boolean allowed = workspaceId.equals(membership.workspaceId()) && membership.isMember();
      meterRegistry
          .counter(
              "search_permission_runtime_checks_total",
              "check",
              "workspace_membership",
              "result",
              allowed ? "allowed" : "denied")
          .increment();
      return allowed;
    } catch (Exception e) {
      log.error(
          "Workspace membership check failed userId={} workspaceId={}",
          userId,
          workspaceId,
          e);
      meterRegistry
          .counter("search_permission_runtime_checks_total", "check", "workspace_membership", "result", "failed")
          .increment();
      // fail-closed
      return false;
    }
  }

  public boolean canReadRestrictedNotebook(UUID userId, UUID workspaceId, UUID notebookId) {
    if (notebookId == null) {
      return true;
    }
    try {
      WorkspaceClient.NotebookPermissionResponse permission =
          workspaceClient.notebookPermissions(notebookId, userId);
      boolean allowed = workspaceId.equals(permission.workspaceId()) && permission.canRead();
      meterRegistry
          .counter(
              "search_permission_runtime_checks_total",
              "check",
              "notebook_permission",
              "result",
              allowed ? "allowed" : "denied")
          .increment();
      return allowed;
    } catch (Exception e) {
      log.error(
          "Workspace permission check failed userId={} workspaceId={} notebookId={}",
          userId,
          workspaceId,
          notebookId,
          e);
      meterRegistry
          .counter("search_permission_runtime_checks_total", "check", "notebook_permission", "result", "failed")
          .increment();
      // fail-closed for restricted documents
      return false;
    }
  }
}
