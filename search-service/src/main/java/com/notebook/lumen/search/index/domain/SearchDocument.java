package com.notebook.lumen.search.index.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_documents")
public class SearchDocument {
  @Id private UUID id;
  private UUID workspaceId;
  private UUID notebookId;
  private UUID noteId;
  private String title;
  private String contentText;
  private String tagsText;
  private String notebookName;
  private UUID createdBy;
  private UUID updatedBy;
  private Instant noteCreatedAt;
  private Instant noteUpdatedAt;
  private Instant archivedAt;

  @Column(insertable = false, updatable = false)
  private String searchVector;

  private Instant indexedAt;
  private Integer sourceVersion;
  private Integer permissionVersion;
  private String visibilityMode;
  private boolean workspaceReadable;
  private boolean restricted;
  private Instant permissionIndexedAt;
  private UUID lastSeenReindexJobId;
  private Instant lastSeenReindexAt;
  private Instant createdAt;
  private Instant updatedAt;

  protected SearchDocument() {}

  public SearchDocument(
      UUID id,
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
      Instant now) {
    this.id = id;
    this.createdAt = now;
    apply(
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
        permissionVersion,
        visibilityMode,
        workspaceReadable,
        restricted,
        permissionIndexedAt,
        now);
  }

  public SearchDocument(
      UUID id,
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
      Instant now) {
    this(
        id,
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
        now);
  }

  public boolean newerThan(Integer incomingSourceVersion) {
    return sourceVersion != null
        && incomingSourceVersion != null
        && sourceVersion > incomingSourceVersion;
  }

  public void apply(
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
      Instant now) {
    this.workspaceId = workspaceId;
    this.notebookId = notebookId;
    this.noteId = noteId;
    this.title = title;
    this.contentText = contentText;
    this.tagsText = tagsText;
    this.notebookName = notebookName;
    this.createdBy = createdBy;
    this.updatedBy = updatedBy;
    this.noteCreatedAt = noteCreatedAt;
    this.noteUpdatedAt = noteUpdatedAt;
    this.archivedAt = archivedAt;
    this.sourceVersion = sourceVersion;
    this.permissionVersion = permissionVersion;
    this.visibilityMode = visibilityMode;
    this.workspaceReadable = workspaceReadable;
    this.restricted = restricted;
    this.permissionIndexedAt = permissionIndexedAt;
    this.indexedAt = now;
    this.updatedAt = now;
  }

  public void archive(Instant archivedAt, Instant now) {
    this.archivedAt = archivedAt == null ? now : archivedAt;
    this.indexedAt = now;
    this.updatedAt = now;
  }

  public void markSeenForReindex(UUID jobId, Instant now) {
    this.lastSeenReindexJobId = jobId;
    this.lastSeenReindexAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getNotebookId() {
    return notebookId;
  }

  public UUID getNoteId() {
    return noteId;
  }

  public String getTitle() {
    return title;
  }

  public String getContentText() {
    return contentText;
  }

  public String getTagsText() {
    return tagsText;
  }

  public String getNotebookName() {
    return notebookName;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getNoteCreatedAt() {
    return noteCreatedAt;
  }

  public Instant getNoteUpdatedAt() {
    return noteUpdatedAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public Instant getIndexedAt() {
    return indexedAt;
  }

  public Integer getSourceVersion() {
    return sourceVersion;
  }

  public Integer getPermissionVersion() {
    return permissionVersion;
  }

  public String getVisibilityMode() {
    return visibilityMode;
  }

  public boolean isWorkspaceReadable() {
    return workspaceReadable;
  }

  public boolean isRestricted() {
    return restricted;
  }

  public Instant getPermissionIndexedAt() {
    return permissionIndexedAt;
  }

  public UUID getLastSeenReindexJobId() {
    return lastSeenReindexJobId;
  }

  public Instant getLastSeenReindexAt() {
    return lastSeenReindexAt;
  }
}
