package com.notebook.lumen.workspace.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notebook_members")
public class NotebookMember {
  @EmbeddedId private NotebookMemberId id;
  private UUID workspaceId;

  @Enumerated(EnumType.STRING)
  private NotebookRole role;

  private Instant createdAt;
  private Instant updatedAt;

  protected NotebookMember() {}

  public NotebookMember(
      UUID notebookId, UUID workspaceId, UUID userId, NotebookRole role, Instant now) {
    this.id = new NotebookMemberId(notebookId, userId);
    this.workspaceId = workspaceId;
    this.role = role;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public NotebookMemberId getId() {
    return id;
  }

  public UUID getNotebookId() {
    return id.getNotebookId();
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getUserId() {
    return id.getUserId();
  }

  public NotebookRole getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void changeRole(NotebookRole role, Instant now) {
    this.role = role;
    this.updatedAt = now;
  }
}
