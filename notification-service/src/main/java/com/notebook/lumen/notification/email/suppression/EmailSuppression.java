package com.notebook.lumen.notification.email.suppression;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_suppressions")
public class EmailSuppression {
  @Id private UUID id;
  private String email;

  @Enumerated(EnumType.STRING)
  private EmailSuppressionReason reason;

  private String provider;
  private String providerEventId;
  private String source;
  private Instant createdAt;
  private Instant expiresAt;
  private Instant releasedAt;

  protected EmailSuppression() {}

  public EmailSuppression(
      UUID id,
      String email,
      EmailSuppressionReason reason,
      String provider,
      String providerEventId,
      String source,
      Instant createdAt,
      Instant expiresAt) {
    this.id = id;
    this.email = email;
    this.reason = reason;
    this.provider = provider;
    this.providerEventId = providerEventId;
    this.source = source;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public EmailSuppressionReason getReason() {
    return reason;
  }

  public String getProvider() {
    return provider;
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public String getSource() {
    return source;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getReleasedAt() {
    return releasedAt;
  }

  public boolean active(Instant now) {
    return releasedAt == null && (expiresAt == null || expiresAt.isAfter(now));
  }

  public void release(Instant now) {
    this.releasedAt = now;
  }

  public void replace(
      EmailSuppressionReason reason,
      String provider,
      String providerEventId,
      String source,
      Instant now,
      Instant expiresAt) {
    this.reason = reason;
    this.provider = provider;
    this.providerEventId = providerEventId;
    this.source = source;
    this.createdAt = now;
    this.expiresAt = expiresAt;
    this.releasedAt = null;
  }
}
