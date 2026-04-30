package com.notebook.lumen.workspace.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class Workspace {

  @Id private UUID id;
  private String slug;
  private String name;

  @Enumerated(EnumType.STRING)
  private WorkspaceType type;

  private UUID ownerId;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant archivedAt;

  protected Workspace() {}

  public Workspace(
      UUID id,
      String slug,
      String name,
      WorkspaceType type,
      UUID ownerId,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.slug = slug;
    this.name = name;
    this.type = type;
    this.ownerId = ownerId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getSlug() {
    return slug;
  }

  public String getName() {
    return name;
  }

  public WorkspaceType getType() {
    return type;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void update(String name, String slug, Instant now) {
    this.name = name;
    this.slug = slug;
    this.updatedAt = now;
  }

  public void archive(Instant now) {
    this.archivedAt = now;
    this.updatedAt = now;
  }
}
