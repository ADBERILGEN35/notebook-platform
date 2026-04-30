package com.notebook.lumen.content.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "note_tags")
public class NoteTag {
  @EmbeddedId private NoteTagId id;
  private UUID workspaceId;
  private Instant createdAt;

  protected NoteTag() {}

  public NoteTag(UUID noteId, UUID tagId, UUID workspaceId, Instant createdAt) {
    this.id = new NoteTagId(noteId, tagId);
    this.workspaceId = workspaceId;
    this.createdAt = createdAt;
  }

  public NoteTagId getId() {
    return id;
  }

  public UUID getNoteId() {
    return id.getNoteId();
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
