package com.notebook.lumen.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notes")
public class Note {
  @Id private UUID id;
  private UUID workspaceId;
  private UUID notebookId;
  private UUID parentNoteId;
  private String title;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String contentBlocks;

  private int contentSchemaVersion;
  private UUID createdBy;
  private UUID updatedBy;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant archivedAt;

  protected Note() {}

  public Note(
      UUID id,
      UUID workspaceId,
      UUID notebookId,
      UUID parentNoteId,
      String title,
      String contentBlocks,
      int contentSchemaVersion,
      UUID createdBy,
      Instant now) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.notebookId = notebookId;
    this.parentNoteId = parentNoteId;
    this.title = title;
    this.contentBlocks = contentBlocks;
    this.contentSchemaVersion = contentSchemaVersion;
    this.createdBy = createdBy;
    this.createdAt = now;
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

  public UUID getParentNoteId() {
    return parentNoteId;
  }

  public String getTitle() {
    return title;
  }

  public String getContentBlocks() {
    return contentBlocks;
  }

  public int getContentSchemaVersion() {
    return contentSchemaVersion;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void update(
      String title, String contentBlocks, int contentSchemaVersion, UUID updatedBy, Instant now) {
    this.title = title;
    this.contentBlocks = contentBlocks;
    this.contentSchemaVersion = contentSchemaVersion;
    this.updatedBy = updatedBy;
    this.updatedAt = now;
  }

  public void archive(Instant now, UUID updatedBy) {
    this.archivedAt = now;
    this.updatedBy = updatedBy;
    this.updatedAt = now;
  }
}
