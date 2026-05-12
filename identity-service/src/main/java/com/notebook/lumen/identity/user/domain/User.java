package com.notebook.lumen.identity.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "name", nullable = false, length = 160)
  private String name;

  @Column(name = "avatar_url", nullable = true, length = 1024)
  private String avatarUrl;

  @Column(name = "password_hash", nullable = false, length = 512)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private UserStatus status;

  @Column(name = "email_verified_at", nullable = true)
  private Instant emailVerifiedAt;

  @Column(name = "last_login_at", nullable = true)
  private Instant lastLoginAt;

  @Column(name = "password_changed_at", nullable = true)
  private Instant passwordChangedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at", nullable = true)
  private Instant deletedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 40)
  private UserSource source;

  @Column(name = "scim_external_id", nullable = true, length = 255)
  private String scimExternalId;

  @Column(name = "deprovisioned_at", nullable = true)
  private Instant deprovisionedAt;

  @Column(name = "deprovision_reason", nullable = true, length = 120)
  private String deprovisionReason;

  @Column(name = "last_scim_external_id", nullable = true, length = 255)
  private String lastScimExternalId;

  @Column(name = "reactivated_at", nullable = true)
  private Instant reactivatedAt;

  protected User() {
    // JPA
  }

  public User(
      UUID id,
      String email,
      String name,
      String avatarUrl,
      String passwordHash,
      UserStatus status,
      Instant emailVerifiedAt,
      Instant lastLoginAt,
      Instant passwordChangedAt,
      Instant createdAt,
      Instant updatedAt,
      Instant deletedAt) {
    this(
        id,
        email,
        name,
        avatarUrl,
        passwordHash,
        status,
        emailVerifiedAt,
        lastLoginAt,
        passwordChangedAt,
        createdAt,
        updatedAt,
        deletedAt,
        UserSource.LOCAL,
        null,
        null);
  }

  public User(
      UUID id,
      String email,
      String name,
      String avatarUrl,
      String passwordHash,
      UserStatus status,
      Instant emailVerifiedAt,
      Instant lastLoginAt,
      Instant passwordChangedAt,
      Instant createdAt,
      Instant updatedAt,
      Instant deletedAt,
      UserSource source,
      String scimExternalId,
      Instant deprovisionedAt) {
    this.id = id;
    this.email = email;
    this.name = name;
    this.avatarUrl = avatarUrl;
    this.passwordHash = passwordHash;
    this.status = status;
    this.emailVerifiedAt = emailVerifiedAt;
    this.lastLoginAt = lastLoginAt;
    this.passwordChangedAt = passwordChangedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.deletedAt = deletedAt;
    this.source = source == null ? UserSource.LOCAL : source;
    this.scimExternalId = scimExternalId;
    this.deprovisionedAt = deprovisionedAt;
  }

  @PrePersist
  void onPrePersist() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
    if (source == null) {
      source = UserSource.LOCAL;
    }
  }

  @PreUpdate
  void onPreUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
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

  public UserSource getSource() {
    return source;
  }

  public String getScimExternalId() {
    return scimExternalId;
  }

  public Instant getDeprovisionedAt() {
    return deprovisionedAt;
  }

  public String getDeprovisionReason() {
    return deprovisionReason;
  }

  public String getLastScimExternalId() {
    return lastScimExternalId;
  }

  public Instant getReactivatedAt() {
    return reactivatedAt;
  }

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  public void setSource(UserSource source) {
    this.source = source;
  }

  public void setScimExternalId(String scimExternalId) {
    this.scimExternalId = scimExternalId;
  }

  public void deactivateByScim(Instant now) {
    this.status = UserStatus.DISABLED;
    this.deprovisionedAt = now;
    this.deprovisionReason = "SCIM_ACTIVE_FALSE_OR_DELETE";
    this.lastScimExternalId = this.scimExternalId;
  }

  public void reactivateByScim() {
    this.status = UserStatus.ACTIVE;
    this.deprovisionedAt = null;
    this.deprovisionReason = null;
    this.reactivatedAt = Instant.now();
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}
