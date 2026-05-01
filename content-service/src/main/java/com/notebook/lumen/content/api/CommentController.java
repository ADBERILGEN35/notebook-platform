package com.notebook.lumen.content.api;

import com.notebook.lumen.content.dto.CommentResponse;
import com.notebook.lumen.content.dto.PageResponse;
import com.notebook.lumen.content.dto.Requests.*;
import com.notebook.lumen.content.service.CommentService;
import com.notebook.lumen.content.shared.Pagination;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.UserContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommentController {
  private final CommentService commentService;
  private final UserContextResolver userContextResolver;

  public CommentController(CommentService commentService, UserContextResolver userContextResolver) {
    this.commentService = commentService;
    this.userContextResolver = userContextResolver;
  }

  @PostMapping("/notes/{noteId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create comment")
  public CommentResponse create(
      @PathVariable UUID noteId,
      @Valid @RequestBody CreateCommentRequest request,
      HttpServletRequest http) {
    return commentService.create(user(http), noteId, request);
  }

  @GetMapping("/notes/{noteId}/comments")
  @Operation(summary = "List comments")
  public PageResponse<CommentResponse> list(
      @PathVariable UUID noteId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      HttpServletRequest http) {
    return commentService.list(
        user(http), noteId, Pagination.pageable(page, size, sort, CommentService.COMMENT_SORTS));
  }

  @PatchMapping("/comments/{commentId}")
  @Operation(summary = "Update comment")
  public CommentResponse update(
      @PathVariable UUID commentId,
      @Valid @RequestBody UpdateCommentRequest request,
      HttpServletRequest http) {
    return commentService.update(user(http), commentId, request);
  }

  @DeleteMapping("/comments/{commentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete comment")
  public void delete(@PathVariable UUID commentId, HttpServletRequest http) {
    commentService.delete(user(http), commentId);
  }

  @PostMapping("/comments/{commentId}/resolve")
  @Operation(summary = "Resolve comment")
  public CommentResponse resolve(@PathVariable UUID commentId, HttpServletRequest http) {
    return commentService.resolve(user(http), commentId);
  }

  @PostMapping("/comments/{commentId}/reopen")
  @Operation(summary = "Reopen comment")
  public CommentResponse reopen(@PathVariable UUID commentId, HttpServletRequest http) {
    return commentService.reopen(user(http), commentId);
  }

  private UserContext user(HttpServletRequest request) {
    return userContextResolver.require(request);
  }
}
