package com.notebook.lumen.workspace.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_members")
public class WorkspaceMember {

  @EmbeddedId private WorkspaceMemberId id;

  @Enumerated(EnumType.STRING)
  private WorkspaceRole role;

  private Instant joinedAt;
  private Instant createdAt;
  private Instant updatedAt;

  protected WorkspaceMember() {}

  public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role, Instant now) {
    this.id = new WorkspaceMemberId(workspaceId, userId);
    this.role = role;
    this.joinedAt = now;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public WorkspaceMemberId getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return id.getWorkspaceId();
  }

  public UUID getUserId() {
    return id.getUserId();
  }

  public WorkspaceRole getRole() {
    return role;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void changeRole(WorkspaceRole role, Instant now) {
    this.role = role;
    this.updatedAt = now;
  }
}
