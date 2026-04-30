package com.notebook.lumen.content.service;

import com.notebook.lumen.content.client.WorkspaceClient;
import com.notebook.lumen.content.shared.exception.ContentException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class PermissionService {
  private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
  private final WorkspaceClient workspaceClient;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public PermissionService(
      WorkspaceClient workspaceClient,
      CircuitBreaker workspacePermissionCircuitBreaker,
      Retry workspacePermissionRetry) {
    this.workspaceClient = workspaceClient;
    this.circuitBreaker = workspacePermissionCircuitBreaker;
    this.retry = workspacePermissionRetry;
  }

  public WorkspaceClient.NotebookPermissionResponse requireReadable(UUID userId, UUID notebookId) {
    WorkspaceClient.NotebookPermissionResponse permission = permissions(userId, notebookId);
    if (!permission.canRead()) throw denied();
    return permission;
  }

  public WorkspaceClient.NotebookPermissionResponse requireWritable(UUID userId, UUID notebookId) {
    WorkspaceClient.NotebookPermissionResponse permission = permissions(userId, notebookId);
    if (!permission.canEdit()) throw denied();
    return permission;
  }

  public WorkspaceClient.NotebookPermissionResponse requireCommentable(
      UUID userId, UUID notebookId) {
    WorkspaceClient.NotebookPermissionResponse permission = permissions(userId, notebookId);
    if (!permission.canComment()) throw denied();
    return permission;
  }

  public boolean canManage(UUID userId, UUID notebookId) {
    return permissions(userId, notebookId).canManage();
  }

  public void requireTag(UUID userId, UUID workspaceId, UUID tagId) {
    try {
      Supplier<WorkspaceClient.TagExistsResponse> call =
          () -> workspaceClient.tagExists(workspaceId, tagId, "NOTE");
      if (!executeGet(call).exists()) {
        throw new ContentException(
            HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "Tag not found in workspace");
      }
    } catch (ContentException e) {
      throw e;
    } catch (Exception e) {
      log.error("Workspace tag check failed workspaceId={} tagId={}", workspaceId, tagId, e);
      throw new ContentException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "WORKSPACE_SERVICE_UNAVAILABLE",
          "Workspace service unavailable");
    }
  }

  private WorkspaceClient.NotebookPermissionResponse permissions(UUID userId, UUID notebookId) {
    try {
      Supplier<WorkspaceClient.NotebookPermissionResponse> call =
          () -> workspaceClient.notebookPermissions(notebookId, userId);
      return executeGet(call);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        throw new ContentException(
            HttpStatus.NOT_FOUND, "NOTEBOOK_NOT_FOUND", "Notebook not found");
      }
      log.error(
          "Workspace permission check returned error userId={} notebookId={} status={}",
          userId,
          notebookId,
          e.getStatusCode().value(),
          e);
      throw new ContentException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "WORKSPACE_SERVICE_UNAVAILABLE",
          "Workspace service unavailable");
    } catch (Exception e) {
      log.error("Workspace permission check failed userId={} notebookId={}", userId, notebookId, e);
      throw new ContentException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "WORKSPACE_SERVICE_UNAVAILABLE",
          "Workspace service unavailable");
    }
  }

  private ContentException denied() {
    return new ContentException(
        HttpStatus.FORBIDDEN, "NOTEBOOK_ACCESS_DENIED", "Insufficient notebook permission");
  }

  private <T> T executeGet(Supplier<T> supplier) {
    return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, supplier))
        .get();
  }
}
