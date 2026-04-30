package com.notebook.lumen.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {
  @Id private UUID id;
  private UUID workspaceId;
  private UUID noteId;
  private UUID userId;
  private UUID parentCommentId;
  private String blockId;
  private String content;
  private Instant resolvedAt;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  protected Comment() {}

  public Comment(
      UUID id,
      UUID workspaceId,
      UUID noteId,
      UUID userId,
      UUID parentCommentId,
      String blockId,
      String content,
      Instant now) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.noteId = noteId;
    this.userId = userId;
    this.parentCommentId = parentCommentId;
    this.blockId = blockId;
    this.content = content;
    this.createdAt = now;
    this.updatedAt = now;
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

  public UUID getUserId() {
    return userId;
  }

  public UUID getParentCommentId() {
    return parentCommentId;
  }

  public String getBlockId() {
    return blockId;
  }

  public String getContent() {
    return content;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void update(String content, Instant now) {
    this.content = content;
    this.updatedAt = now;
  }

  public void resolve(Instant now) {
    this.resolvedAt = now;
    this.updatedAt = now;
  }

  public void reopen(Instant now) {
    this.resolvedAt = null;
    this.updatedAt = now;
  }

  public void delete(Instant now) {
    this.deletedAt = now;
    this.updatedAt = now;
  }
}
