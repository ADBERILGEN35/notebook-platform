package com.notebook.lumen.search.query.dto;

import java.time.Instant;
import java.util.UUID;

public record SearchNoteResult(
    UUID noteId,
    UUID workspaceId,
    UUID notebookId,
    String title,
    String snippet,
    double rank,
    Instant noteUpdatedAt,
    String visibilityMode,
    Integer permissionVersion,
    boolean workspaceReadable,
    boolean restricted) {
  public SearchNoteResult(
      UUID noteId,
      UUID workspaceId,
      UUID notebookId,
      String title,
      String snippet,
      double rank,
      Instant noteUpdatedAt) {
    this(noteId, workspaceId, notebookId, title, snippet, rank, noteUpdatedAt, null, null, true, false);
  }
}
