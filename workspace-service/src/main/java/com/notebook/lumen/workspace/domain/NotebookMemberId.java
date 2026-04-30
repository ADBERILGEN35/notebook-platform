package com.notebook.lumen.workspace.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class NotebookMemberId implements Serializable {
  private UUID notebookId;
  private UUID userId;

  protected NotebookMemberId() {}

  public NotebookMemberId(UUID notebookId, UUID userId) {
    this.notebookId = notebookId;
    this.userId = userId;
  }

  public UUID getNotebookId() {
    return notebookId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NotebookMemberId that)) return false;
    return java.util.Objects.equals(notebookId, that.notebookId)
        && java.util.Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(notebookId, userId);
  }
}
