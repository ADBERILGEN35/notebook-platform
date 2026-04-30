package com.notebook.lumen.content.api;

import com.notebook.lumen.content.dto.NoteTagResponse;
import com.notebook.lumen.content.service.NoteTagService;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.UserContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class NoteTagController {
  private final NoteTagService noteTagService;
  private final UserContextResolver userContextResolver;

  public NoteTagController(NoteTagService noteTagService, UserContextResolver userContextResolver) {
    this.noteTagService = noteTagService;
    this.userContextResolver = userContextResolver;
  }

  @PutMapping("/notes/{noteId}/tags/{tagId}")
  @Operation(summary = "Attach tag to note")
  public NoteTagResponse attach(
      @PathVariable UUID noteId, @PathVariable UUID tagId, HttpServletRequest http) {
    return noteTagService.attach(user(http), noteId, tagId);
  }

  @DeleteMapping("/notes/{noteId}/tags/{tagId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Detach tag from note")
  public void detach(@PathVariable UUID noteId, @PathVariable UUID tagId, HttpServletRequest http) {
    noteTagService.detach(user(http), noteId, tagId);
  }

  @GetMapping("/notes/{noteId}/tags")
  @Operation(summary = "List note tags")
  public List<NoteTagResponse> list(@PathVariable UUID noteId, HttpServletRequest http) {
    return noteTagService.list(user(http), noteId);
  }

  private UserContext user(HttpServletRequest request) {
    return userContextResolver.require(request);
  }
}
