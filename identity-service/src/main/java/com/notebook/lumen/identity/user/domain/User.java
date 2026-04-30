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

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }
}
