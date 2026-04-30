package com.notebook.lumen.workspace.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class NotebookTagId implements Serializable {
  private UUID notebookId;
  private UUID tagId;

  protected NotebookTagId() {}

  public NotebookTagId(UUID notebookId, UUID tagId) {
    this.notebookId = notebookId;
    this.tagId = tagId;
  }

  public UUID getNotebookId() {
    return notebookId;
  }

  public UUID getTagId() {
    return tagId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NotebookTagId that)) return false;
    return java.util.Objects.equals(notebookId, that.notebookId)
        && java.util.Objects.equals(tagId, that.tagId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(notebookId, tagId);
  }
}
