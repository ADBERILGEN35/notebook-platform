package com.notebook.lumen.content.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class NoteLinkId implements Serializable {
  private UUID fromNoteId;
  private UUID toNoteId;

  protected NoteLinkId() {}

  public NoteLinkId(UUID fromNoteId, UUID toNoteId) {
    this.fromNoteId = fromNoteId;
    this.toNoteId = toNoteId;
  }

  public UUID getFromNoteId() {
    return fromNoteId;
  }

  public UUID getToNoteId() {
    return toNoteId;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof NoteLinkId that
        && java.util.Objects.equals(fromNoteId, that.fromNoteId)
        && java.util.Objects.equals(toNoteId, that.toNoteId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(fromNoteId, toNoteId);
  }
}
