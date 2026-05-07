package com.notebook.lumen.content.api;

import com.notebook.lumen.content.dto.*;
import com.notebook.lumen.content.dto.Requests.*;
import com.notebook.lumen.content.service.NoteEtagSupport;
import com.notebook.lumen.content.service.NoteService;
import com.notebook.lumen.content.shared.Pagination;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.UserContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
public class NoteController {
  private final NoteService noteService;
  private final NoteEtagSupport noteEtagSupport;
  private final UserContextResolver userContextResolver;

  public NoteController(
      NoteService noteService, NoteEtagSupport noteEtagSupport, UserContextResolver userContextResolver) {
    this.noteService = noteService;
    this.noteEtagSupport = noteEtagSupport;
    this.userContextResolver = userContextResolver;
  }

  @PostMapping("/notebooks/{notebookId}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create note")
  public NoteResponse create(
      @PathVariable UUID notebookId,
      @Valid @RequestBody CreateNoteRequest request,
      HttpServletRequest http) {
    return noteService.create(user(http), notebookId, request);
  }

  @GetMapping("/notes/{noteId}")
  @Operation(summary = "Get note")
  public ResponseEntity<NoteResponse> get(@PathVariable UUID noteId, HttpServletRequest http) {
    NoteResponse response = noteService.get(user(http), noteId);
    return withEtag(response);
  }

  @GetMapping("/notebooks/{notebookId}/notes")
  @Operation(summary = "List notes")
  public PageResponse<NoteResponse> list(
      @PathVariable UUID notebookId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      HttpServletRequest http) {
    return noteService.list(
        user(http), notebookId, Pagination.pageable(page, size, sort, NoteService.NOTE_SORTS));
  }

  @PatchMapping("/notes/{noteId}")
  @Operation(summary = "Update note")
  public ResponseEntity<NoteResponse> update(
      @PathVariable UUID noteId,
      @Valid @RequestBody UpdateNoteRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpServletRequest http) {
    NoteResponse response = noteService.update(user(http), noteId, request, ifMatch);
    return withEtag(response);
  }

  @DeleteMapping("/notes/{noteId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Archive note")
  public void archive(@PathVariable UUID noteId, HttpServletRequest http) {
    noteService.archive(user(http), noteId);
  }

  @GetMapping("/notes/{noteId}/versions")
  @Operation(summary = "List note versions")
  public PageResponse<NoteVersionResponse> versions(
      @PathVariable UUID noteId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      HttpServletRequest http) {
    return noteService.versions(
        user(http), noteId, Pagination.pageable(page, size, sort, NoteService.VERSION_SORTS));
  }

  @GetMapping("/notes/{noteId}/versions/{versionNumber}")
  @Operation(summary = "Get note version")
  public NoteVersionResponse version(
      @PathVariable UUID noteId, @PathVariable int versionNumber, HttpServletRequest http) {
    return noteService.version(user(http), noteId, versionNumber);
  }

  @PostMapping("/notes/{noteId}/restore/{versionNumber}")
  @Operation(summary = "Restore note version")
  public ResponseEntity<NoteResponse> restore(
      @PathVariable UUID noteId,
      @PathVariable int versionNumber,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpServletRequest http) {
    NoteResponse response = noteService.restore(user(http), noteId, versionNumber, ifMatch);
    return withEtag(response);
  }

  @GetMapping("/notes/{noteId}/links/outgoing")
  @Operation(summary = "Outgoing note links")
  public PageResponse<NoteLinkResponse> outgoing(
      @PathVariable UUID noteId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      HttpServletRequest http) {
    return noteService.outgoing(
        user(http), noteId, Pagination.pageable(page, size, sort, NoteService.LINK_SORTS));
  }

  @GetMapping({"/notes/{noteId}/links/incoming", "/notes/{noteId}/backlinks"})
  @Operation(summary = "Incoming note links")
  public PageResponse<NoteLinkResponse> incoming(
      @PathVariable UUID noteId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      HttpServletRequest http) {
    return noteService.incoming(
        user(http), noteId, Pagination.pageable(page, size, sort, NoteService.LINK_SORTS));
  }

  @GetMapping("/notes/search")
  @Operation(summary = "Search notes")
  public PageResponse<NoteResponse> search(
      @RequestParam UUID workspaceId,
      @RequestParam @Size(min = 1, max = 120) String q,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      HttpServletRequest http) {
    return noteService.search(
        user(http),
        workspaceId,
        q,
        Pagination.pageable(page, size, sort, NoteService.SEARCH_SORTS));
  }

  private UserContext user(HttpServletRequest request) {
    return userContextResolver.require(request);
  }

  private ResponseEntity<NoteResponse> withEtag(NoteResponse response) {
    return ResponseEntity.ok()
        .eTag(noteEtagSupport.buildEtag(response.noteRevision()))
        .body(response);
  }
}
