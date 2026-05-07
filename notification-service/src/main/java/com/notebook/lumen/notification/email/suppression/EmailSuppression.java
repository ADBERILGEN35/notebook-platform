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
}
