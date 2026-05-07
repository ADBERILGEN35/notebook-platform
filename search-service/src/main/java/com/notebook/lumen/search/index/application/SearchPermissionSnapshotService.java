package com.notebook.lumen.search.index.application;

import com.notebook.lumen.search.query.application.WorkspaceClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.notebook.lumen.search.shared.config.SearchProperties;

@Service
public class SearchPermissionSnapshotService {
  private static final Logger log = LoggerFactory.getLogger(SearchPermissionSnapshotService.class);

  private final WorkspaceClient workspaceClient;
  private final SearchAuditService auditService;
  private final MeterRegistry meterRegistry;
  private final SearchProperties properties;

  public SearchPermissionSnapshotService(
      WorkspaceClient workspaceClient,
      SearchAuditService auditService,
      MeterRegistry meterRegistry,
      SearchProperties properties) {
    this.workspaceClient = workspaceClient;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
    this.properties = properties;
  }

  public PermissionSnapshot resolve(UUID workspaceId, UUID notebookId) {
    if (notebookId == null) {
      return new PermissionSnapshot(
          workspaceId, null, "WORKSPACE", true, false, null, Instant.now(), false);
    }
    if (properties != null && !properties.permissionSnapshotEnabled()) {
      return new PermissionSnapshot(workspaceId, notebookId, "RESTRICTED", false, true, null, null, false);
    }
    try {
      WorkspaceClient.SearchPermissionSnapshotResponse response =
          workspaceClient.searchPermissionSnapshot(notebookId);
      meterRegistry.counter("search_permission_snapshot_fetch_total", "status", "success").increment();
      auditService.record(
          "SEARCH_PERMISSION_SNAPSHOT_INDEXED",
          response.workspaceId(),
          notebookId,
          Map.of(
              "notebookId",
              notebookId.toString(),
              "visibilityMode",
              response.visibilityMode() == null ? "UNKNOWN" : response.visibilityMode(),
              "restricted",
              String.valueOf(response.restricted())));
      return new PermissionSnapshot(
          response.workspaceId(),
          response.notebookId(),
          response.visibilityMode(),
          response.workspaceReadable(),
          response.restricted(),
          response.permissionVersion(),
          response.updatedAt(),
          true);
    } catch (Exception ex) {
      meterRegistry.counter("search_permission_snapshot_fetch_total", "status", "failure").increment();
      meterRegistry.counter("search_permission_snapshot_fetch_failure_total").increment();
      log.warn(
          "Failed to fetch permission snapshot workspaceId={} notebookId={}",
          workspaceId,
          notebookId,
          ex);
      auditService.record(
          "SEARCH_PERMISSION_SNAPSHOT_FETCH_FAILED",
          workspaceId,
          notebookId,
          Map.of("notebookId", notebookId.toString()));
      // fail-closed fallback
      return new PermissionSnapshot(workspaceId, notebookId, "RESTRICTED", false, true, null, null, false);
    }
  }

  public record PermissionSnapshot(
      UUID workspaceId,
      UUID notebookId,
      String visibilityMode,
      boolean workspaceReadable,
      boolean restricted,
      Integer permissionVersion,
      Instant permissionIndexedAt,
      boolean fromSource) {}
}

