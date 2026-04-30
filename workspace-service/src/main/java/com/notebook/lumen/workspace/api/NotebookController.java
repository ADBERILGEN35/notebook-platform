package com.notebook.lumen.workspace.api;

import com.notebook.lumen.workspace.dto.*;
import com.notebook.lumen.workspace.dto.Requests.*;
import com.notebook.lumen.workspace.service.NotebookService;
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
public class NotebookController {
  private final NotebookService notebookService;
  private final UserContextResolver userContextResolver;

  public NotebookController(
      NotebookService notebookService, UserContextResolver userContextResolver) {
    this.notebookService = notebookService;
    this.userContextResolver = userContextResolver;
  }

  @PostMapping("/workspaces/{workspaceId}/notebooks")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create notebook")
  public NotebookResponse create(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateNotebookRequest request,
      HttpServletRequest httpRequest) {
    return notebookService.create(user(httpRequest), workspaceId, request);
  }

  @GetMapping("/workspaces/{workspaceId}/notebooks")
  @Operation(summary = "List notebooks")
  public List<NotebookResponse> list(
      @PathVariable UUID workspaceId, HttpServletRequest httpRequest) {
    return notebookService.list(user(httpRequest), workspaceId);
  }

  @GetMapping("/notebooks/{notebookId}")
  @Operation(summary = "Get notebook")
  public NotebookResponse get(@PathVariable UUID notebookId, HttpServletRequest httpRequest) {
    return notebookService.get(user(httpRequest), notebookId);
  }

  @PatchMapping("/notebooks/{notebookId}")
  @Operation(summary = "Update notebook")
  public NotebookResponse update(
      @PathVariable UUID notebookId,
      @Valid @RequestBody UpdateNotebookRequest request,
      HttpServletRequest httpRequest) {
    return notebookService.update(user(httpRequest), notebookId, request);
  }

  @DeleteMapping("/notebooks/{notebookId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Archive notebook")
  public void archive(@PathVariable UUID notebookId, HttpServletRequest httpRequest) {
    notebookService.archive(user(httpRequest), notebookId);
  }

  @GetMapping("/notebooks/{notebookId}/members")
  @Operation(summary = "List notebook members")
  public List<NotebookMemberResponse> members(
      @PathVariable UUID notebookId, HttpServletRequest httpRequest) {
    return notebookService.members(user(httpRequest), notebookId);
  }

  @PutMapping("/notebooks/{notebookId}/members/{userId}")
  @Operation(summary = "Upsert notebook member")
  public NotebookMemberResponse upsertMember(
      @PathVariable UUID notebookId,
      @PathVariable UUID userId,
      @Valid @RequestBody UpsertNotebookMemberRequest request,
      HttpServletRequest httpRequest) {
    return notebookService.upsertMember(user(httpRequest), notebookId, userId, request);
  }

  @PatchMapping("/notebooks/{notebookId}/members/{userId}/role")
  @Operation(summary = "Update notebook member role")
  public NotebookMemberResponse updateMemberRole(
      @PathVariable UUID notebookId,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateNotebookMemberRoleRequest request,
      HttpServletRequest httpRequest) {
    return notebookService.updateMemberRole(user(httpRequest), notebookId, userId, request);
  }

  @DeleteMapping("/notebooks/{notebookId}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove notebook member")
  public void removeMember(
      @PathVariable UUID notebookId, @PathVariable UUID userId, HttpServletRequest httpRequest) {
    notebookService.removeMember(user(httpRequest), notebookId, userId);
  }

  private UserContext user(HttpServletRequest request) {
    return userContextResolver.require(request);
  }
}
