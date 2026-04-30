package com.notebook.lumen.workspace.api;

import com.notebook.lumen.workspace.dto.*;
import com.notebook.lumen.workspace.dto.Requests.*;
import com.notebook.lumen.workspace.service.WorkspaceService;
import com.notebook.lumen.workspace.shared.UserContext;
import com.notebook.lumen.workspace.shared.UserContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkspaceController {

  private final WorkspaceService workspaceService;
  private final UserContextResolver userContextResolver;

  public WorkspaceController(
      WorkspaceService workspaceService, UserContextResolver userContextResolver) {
    this.workspaceService = workspaceService;
    this.userContextResolver = userContextResolver;
  }

  @PostMapping("/workspaces")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create workspace")
  public WorkspaceResponse create(
      @Valid @RequestBody CreateWorkspaceRequest request, HttpServletRequest httpRequest) {
    return workspaceService.create(user(httpRequest), request);
  }

  @GetMapping("/workspaces")
  @Operation(summary = "List current user's workspaces")
  public List<WorkspaceResponse> list(HttpServletRequest httpRequest) {
    return workspaceService.list(user(httpRequest));
  }

  @GetMapping("/workspaces/{workspaceId}")
  @Operation(summary = "Get workspace")
  public WorkspaceResponse get(@PathVariable UUID workspaceId, HttpServletRequest httpRequest) {
    return workspaceService.get(user(httpRequest), workspaceId);
  }

  @PatchMapping("/workspaces/{workspaceId}")
  @Operation(summary = "Update workspace")
  public WorkspaceResponse update(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody UpdateWorkspaceRequest request,
      HttpServletRequest httpRequest) {
    return workspaceService.update(user(httpRequest), workspaceId, request);
  }

  @DeleteMapping("/workspaces/{workspaceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Archive workspace")
  public void archive(@PathVariable UUID workspaceId, HttpServletRequest httpRequest) {
    workspaceService.archive(user(httpRequest), workspaceId);
  }

  @GetMapping("/workspaces/{workspaceId}/members")
  @Operation(summary = "List workspace members")
  public List<WorkspaceMemberResponse> members(
      @PathVariable UUID workspaceId, HttpServletRequest httpRequest) {
    return workspaceService.members(user(httpRequest), workspaceId);
  }

  @PatchMapping("/workspaces/{workspaceId}/members/{userId}/role")
  @Operation(summary = "Update workspace member role")
  public WorkspaceMemberResponse updateRole(
      @PathVariable UUID workspaceId,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateWorkspaceMemberRoleRequest request,
      HttpServletRequest httpRequest) {
    return workspaceService.updateMemberRole(user(httpRequest), workspaceId, userId, request);
  }

  @DeleteMapping("/workspaces/{workspaceId}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove workspace member")
  public void remove(
      @PathVariable UUID workspaceId, @PathVariable UUID userId, HttpServletRequest httpRequest) {
    workspaceService.removeMember(user(httpRequest), workspaceId, userId);
  }

  private UserContext user(HttpServletRequest request) {
    return userContextResolver.require(request);
  }
}
