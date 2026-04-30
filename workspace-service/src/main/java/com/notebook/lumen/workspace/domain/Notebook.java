package com.notebook.lumen.workspace.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notebooks")
public class Notebook {
  @Id private UUID id;
  private UUID workspaceId;
  private String name;
  private String icon;
  private UUID createdBy;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant archivedAt;

  protected Notebook() {}

  public Notebook(
      UUID id, UUID workspaceId, String name, String icon, UUID createdBy, Instant now) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.name = name;
    this.icon = icon;
    this.createdBy = createdBy;
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

  public String getIcon() {
    return icon;
  }

  public UUID getCreatedBy() {
    return createdBy;
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

  public void update(String name, String icon, Instant now) {
    this.name = name;
    this.icon = icon;
    this.updatedAt = now;
  }

  public void archive(Instant now) {
    this.archivedAt = now;
    this.updatedAt = now;
  }
}
