package com.notebook.lumen.workspace.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class WorkspaceMemberId implements Serializable {
  private UUID workspaceId;
  private UUID userId;

  protected WorkspaceMemberId() {}

  public WorkspaceMemberId(UUID workspaceId, UUID userId) {
    this.workspaceId = workspaceId;
    this.userId = userId;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WorkspaceMemberId that)) return false;
    return java.util.Objects.equals(workspaceId, that.workspaceId)
        && java.util.Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(workspaceId, userId);
  }
}
