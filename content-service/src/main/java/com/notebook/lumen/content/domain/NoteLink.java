package com.notebook.lumen.content.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "note_links")
public class NoteLink {
  @EmbeddedId private NoteLinkId id;
  private UUID workspaceId;
  private Instant createdAt;

  protected NoteLink() {}

  public NoteLink(UUID fromNoteId, UUID toNoteId, UUID workspaceId, Instant createdAt) {
    this.id = new NoteLinkId(fromNoteId, toNoteId);
    this.workspaceId = workspaceId;
    this.createdAt = createdAt;
  }

  public NoteLinkId getId() {
    return id;
  }

  public UUID getFromNoteId() {
    return id.getFromNoteId();
  }

  public UUID getToNoteId() {
    return id.getToNoteId();
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
