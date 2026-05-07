package com.notebook.lumen.notification.email.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_provider_events")
public class EmailProviderEvent {
  @Id private UUID id;
  private String provider;
  private String providerEventId;
  private String providerMessageId;

  @Enumerated(EnumType.STRING)
  private EmailProviderEventType eventType;

  private String recipientEmail;
  private Instant occurredAt;

  @Column(columnDefinition = "jsonb")
  private String payload;

  private Instant createdAt;

  protected EmailProviderEvent() {}

  public EmailProviderEvent(
      UUID id,
      String provider,
      String providerEventId,
      String providerMessageId,
      EmailProviderEventType eventType,
      String recipientEmail,
      Instant occurredAt,
      String payload,
      Instant createdAt) {
    this.id = id;
    this.provider = provider;
    this.providerEventId = providerEventId;
    this.providerMessageId = providerMessageId;
    this.eventType = eventType;
    this.recipientEmail = recipientEmail;
    this.occurredAt = occurredAt;
    this.payload = payload;
    this.createdAt = createdAt;
  }

  public String getProviderEventId() {
    return providerEventId;
  }
}
