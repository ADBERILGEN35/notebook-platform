package com.notebook.lumen.identity.scim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scim_groups")
public class ScimGroup {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "external_id", nullable = false, length = 255)
  private String externalId;

  @Column(name = "display_name", nullable = false, length = 255)
  private String displayName;

  @Column(name = "platform_role", length = 80)
  private String platformRole;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ScimGroup() {}

  public ScimGroup(
      UUID id,
      String externalId,
      String displayName,
      String platformRole,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.externalId = externalId;
    this.displayName = displayName;
    this.platformRole = platformRole;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPlatformRole() {
    return platformRole;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(String displayName, String platformRole) {
    this.displayName = displayName;
    this.platformRole = platformRole;
    this.updatedAt = Instant.now();
  }
}
