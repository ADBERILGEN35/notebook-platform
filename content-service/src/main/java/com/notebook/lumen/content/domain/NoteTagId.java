package com.notebook.lumen.content.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class NoteTagId implements Serializable {
  private UUID noteId;
  private UUID tagId;

  protected NoteTagId() {}

  public NoteTagId(UUID noteId, UUID tagId) {
    this.noteId = noteId;
    this.tagId = tagId;
  }

  public UUID getNoteId() {
    return noteId;
  }

  public UUID getTagId() {
    return tagId;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof NoteTagId that
        && java.util.Objects.equals(noteId, that.noteId)
        && java.util.Objects.equals(tagId, that.tagId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(noteId, tagId);
  }
}
