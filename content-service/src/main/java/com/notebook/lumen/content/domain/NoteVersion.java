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
@Table(name = "note_versions")
public class NoteVersion {
  @Id private UUID id;
  private UUID workspaceId;
  private UUID noteId;
  private int versionNumber;
  private String title;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String contentBlocks;

  private int contentSchemaVersion;
  private UUID createdBy;
  private Instant createdAt;

  protected NoteVersion() {}

  public NoteVersion(
      UUID id,
      UUID workspaceId,
      UUID noteId,
      int versionNumber,
      String title,
      String contentBlocks,
      int contentSchemaVersion,
      UUID createdBy,
      Instant createdAt) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.noteId = noteId;
    this.versionNumber = versionNumber;
    this.title = title;
    this.contentBlocks = contentBlocks;
    this.contentSchemaVersion = contentSchemaVersion;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getNoteId() {
    return noteId;
  }

  public int getVersionNumber() {
    return versionNumber;
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}
