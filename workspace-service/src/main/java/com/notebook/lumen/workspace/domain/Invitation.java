package com.notebook.lumen.workspace.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitations")
public class Invitation {
  @Id private UUID id;
  private UUID workspaceId;
  private String email;
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  private WorkspaceRole role;

  private Instant expiresAt;
  private Instant acceptedAt;
  private Instant revokedAt;
  private UUID createdBy;
  private Instant createdAt;

  protected Invitation() {}

  public Invitation(
      UUID id,
      UUID workspaceId,
      String email,
      String tokenHash,
      WorkspaceRole role,
      Instant expiresAt,
      UUID createdBy,
      Instant createdAt) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.email = email;
    this.tokenHash = tokenHash;
    this.role = role;
    this.expiresAt = expiresAt;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getEmail() {
    return email;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public WorkspaceRole getRole() {
    return role;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void accept(Instant now) {
    this.acceptedAt = now;
  }

  public void revoke(Instant now) {
    this.revokedAt = now;
  }
}
