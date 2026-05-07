package com.notebook.lumen.workspace.api;

import com.notebook.lumen.workspace.domain.TagScope;
import com.notebook.lumen.workspace.dto.InternalResponses.NotebookPermissionResponse;
import com.notebook.lumen.workspace.dto.InternalResponses.SearchPermissionSnapshotResponse;
import com.notebook.lumen.workspace.dto.InternalResponses.TagExistsResponse;
import com.notebook.lumen.workspace.dto.InternalResponses.WorkspaceMembershipResponse;
import com.notebook.lumen.workspace.service.InternalWorkspaceService;
import com.notebook.lumen.workspace.shared.InternalApiTokenValidator;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalWorkspaceController {
  private final InternalWorkspaceService internalWorkspaceService;
  private final InternalApiTokenValidator tokenValidator;

  public InternalWorkspaceController(
      InternalWorkspaceService internalWorkspaceService, InternalApiTokenValidator tokenValidator) {
    this.internalWorkspaceService = internalWorkspaceService;
    this.tokenValidator = tokenValidator;
  }

  @GetMapping(
      value = "/internal/notebooks/{notebookId}/permissions",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public NotebookPermissionResponse notebookPermissions(
      @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false)
          String internalToken,
      @RequestHeader(value = InternalApiTokenValidator.SERVICE_AUTH_HEADER_NAME, required = false)
          String serviceAuthorization,
      @PathVariable UUID notebookId,
      @RequestParam UUID userId) {
    tokenValidator.validate(
        internalToken, serviceAuthorization, "internal:workspace:permission:read");
    return internalWorkspaceService.notebookPermissions(notebookId, userId);
  }

  @GetMapping(
      value = "/internal/workspaces/{workspaceId}/permissions",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public WorkspaceMembershipResponse workspaceMembership(
      @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false)
          String internalToken,
      @RequestHeader(value = InternalApiTokenValidator.SERVICE_AUTH_HEADER_NAME, required = false)
          String serviceAuthorization,
      @PathVariable UUID workspaceId,
      @RequestParam UUID userId) {
    tokenValidator.validate(
        internalToken, serviceAuthorization, "internal:workspace:permission:read");
    return internalWorkspaceService.workspaceMembership(workspaceId, userId);
  }

  @GetMapping(
      value = "/internal/notebooks/{notebookId}/search-permission-snapshot",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public SearchPermissionSnapshotResponse searchPermissionSnapshot(
      @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false)
          String internalToken,
      @RequestHeader(value = InternalApiTokenValidator.SERVICE_AUTH_HEADER_NAME, required = false)
          String serviceAuthorization,
      @PathVariable UUID notebookId) {
    tokenValidator.validate(
        internalToken, serviceAuthorization, "internal:workspace:permission:read");
    return internalWorkspaceService.searchPermissionSnapshot(notebookId);
  }

  @GetMapping(
      value = "/internal/workspaces/{workspaceId}/tags/{tagId}/exists",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public TagExistsResponse tagExists(
      @RequestHeader(value = InternalApiTokenValidator.HEADER_NAME, required = false)
          String internalToken,
      @RequestHeader(value = InternalApiTokenValidator.SERVICE_AUTH_HEADER_NAME, required = false)
          String serviceAuthorization,
      @PathVariable UUID workspaceId,
      @PathVariable UUID tagId,
      @RequestParam TagScope scope) {
    tokenValidator.validate(internalToken, serviceAuthorization, "internal:workspace:tag:read");
    return internalWorkspaceService.tagExists(workspaceId, tagId, scope);
  }
}
