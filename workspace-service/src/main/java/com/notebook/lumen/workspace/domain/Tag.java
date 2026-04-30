package com.notebook.lumen.workspace.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tags")
public class Tag {
  @Id private UUID id;
  private UUID workspaceId;
  private String name;
  private String color;

  @Enumerated(EnumType.STRING)
  private TagScope scope;

  private Instant createdAt;
  private Instant updatedAt;
  private Instant archivedAt;

  protected Tag() {}

  public Tag(UUID id, UUID workspaceId, String name, String color, TagScope scope, Instant now) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.name = name;
    this.color = color;
    this.scope = scope;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getName() {
    return name;
  }

  public String getColor() {
    return color;
  }

  public TagScope getScope() {
    return scope;
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

  public void update(String name, String color, Instant now) {
    this.name = name;
    this.color = color;
    this.updatedAt = now;
  }

  public void archive(Instant now) {
    this.archivedAt = now;
    this.updatedAt = now;
  }
}
