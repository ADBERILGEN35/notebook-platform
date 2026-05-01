package com.notebook.lumen.workspace.api;

import com.notebook.lumen.workspace.dto.PageResponse;
import com.notebook.lumen.workspace.dto.Requests.*;
import com.notebook.lumen.workspace.dto.TagResponse;
import com.notebook.lumen.workspace.service.TagService;
import com.notebook.lumen.workspace.shared.Pagination;
import com.notebook.lumen.workspace.shared.UserContext;
import com.notebook.lumen.workspace.shared.UserContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class TagController {
  private final TagService tagService;
  private final UserContextResolver userContextResolver;

  public TagController(TagService tagService, UserContextResolver userContextResolver) {
    this.tagService = tagService;
    this.userContextResolver = userContextResolver;
  }

  @PostMapping("/workspaces/{workspaceId}/tags")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create tag")
  public TagResponse create(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateTagRequest request,
      HttpServletRequest httpRequest) {
    return tagService.create(user(httpRequest), workspaceId, request);
  }

  @GetMapping("/workspaces/{workspaceId}/tags")
  @Operation(summary = "List tags")
  public PageResponse<TagResponse> list(
      @PathVariable UUID workspaceId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      HttpServletRequest httpRequest) {
    return tagService.list(
        user(httpRequest),
        workspaceId,
        Pagination.pageable(page, size, sort, TagService.TAG_SORTS));
  }

  @PatchMapping("/tags/{tagId}")
  @Operation(summary = "Update tag")
  public TagResponse update(
      @PathVariable UUID tagId,
      @Valid @RequestBody UpdateTagRequest request,
      HttpServletRequest httpRequest) {
    return tagService.update(user(httpRequest), tagId, request);
  }

  @DeleteMapping("/tags/{tagId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Archive tag")
  public void archive(@PathVariable UUID tagId, HttpServletRequest httpRequest) {
    tagService.archive(user(httpRequest), tagId);
  }

  @PutMapping("/notebooks/{notebookId}/tags/{tagId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Attach tag to notebook")
  public void attach(
      @PathVariable UUID notebookId, @PathVariable UUID tagId, HttpServletRequest httpRequest) {
    tagService.attach(user(httpRequest), notebookId, tagId);
  }

  @DeleteMapping("/notebooks/{notebookId}/tags/{tagId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Detach tag from notebook")
  public void detach(
      @PathVariable UUID notebookId, @PathVariable UUID tagId, HttpServletRequest httpRequest) {
    tagService.detach(user(httpRequest), notebookId, tagId);
  }

  private UserContext user(HttpServletRequest request) {
    return userContextResolver.require(request);
  }
}
