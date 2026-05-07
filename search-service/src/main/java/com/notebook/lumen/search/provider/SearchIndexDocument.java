package com.notebook.lumen.search.provider;

import java.time.Instant;
import java.util.UUID;

public record SearchIndexDocument(
    UUID documentId,
    UUID workspaceId,
    UUID notebookId,
    UUID noteId,
    String title,
    String contentText,
    String tagsText,
    String notebookName,
    UUID createdBy,
    UUID updatedBy,
    Instant noteCreatedAt,
    Instant noteUpdatedAt,
    Instant archivedAt,
    Integer sourceVersion,
    Integer permissionVersion,
    String visibilityMode,
    boolean workspaceReadable,
    boolean restricted,
    Instant permissionIndexedAt,
    Instant indexedAt) {
  public SearchIndexDocument(
      UUID documentId,
      UUID workspaceId,
      UUID notebookId,
      UUID noteId,
      String title,
      String contentText,
      String tagsText,
      String notebookName,
      UUID createdBy,
      UUID updatedBy,
      Instant noteCreatedAt,
      Instant noteUpdatedAt,
      Instant archivedAt,
      Integer sourceVersion,
      Instant indexedAt) {
    this(
        documentId,
        workspaceId,
        notebookId,
        noteId,
        title,
        contentText,
        tagsText,
        notebookName,
        createdBy,
        updatedBy,
        noteCreatedAt,
        noteUpdatedAt,
        archivedAt,
        sourceVersion,
        null,
        null,
        true,
        false,
        null,
        indexedAt);
  }
}
