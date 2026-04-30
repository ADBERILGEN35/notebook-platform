package com.notebook.lumen.workspace.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notebook_tags")
public class NotebookTag {
  @EmbeddedId private NotebookTagId id;
  private UUID workspaceId;
  private Instant createdAt;

  protected NotebookTag() {}

  public NotebookTag(UUID notebookId, UUID tagId, UUID workspaceId, Instant createdAt) {
    this.id = new NotebookTagId(notebookId, tagId);
    this.workspaceId = workspaceId;
    this.createdAt = createdAt;
  }

  public NotebookTagId getId() {
    return id;
  }

  public UUID getNotebookId() {
    return id.getNotebookId();
  }

  public UUID getTagId() {
    return id.getTagId();
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
