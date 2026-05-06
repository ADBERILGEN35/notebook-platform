package com.notebook.lumen.notification.email.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_notifications")
public class EmailNotification {
  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private EmailNotificationType type;

  private String recipientEmail;
  private String subject;
  private String bodyText;
  private String bodyHtml;

  @Enumerated(EnumType.STRING)
  private EmailNotificationStatus status;

  private String provider;
  private String providerMessageId;
  private String idempotencyKey;
  private int attemptCount;
  private Instant nextAttemptAt;
  private String lastError;
  private Instant sentAt;
  private Instant failedAt;
  private Instant createdAt;
  private Instant updatedAt;

  protected EmailNotification() {}

  public EmailNotification(
      UUID id,
      EmailNotificationType type,
      String recipientEmail,
      String subject,
      String bodyText,
      String bodyHtml,
      String idempotencyKey,
      Instant now) {
    this.id = id;
    this.type = type;
    this.recipientEmail = recipientEmail;
    this.subject = subject;
    this.bodyText = bodyText;
    this.bodyHtml = bodyHtml;
    this.status = EmailNotificationStatus.PENDING;
    this.idempotencyKey = idempotencyKey;
    this.attemptCount = 0;
    this.nextAttemptAt = now;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void markSending(Instant now) {
    this.status = EmailNotificationStatus.SENDING;
    this.updatedAt = now;
  }

  public void markSent(String provider, String providerMessageId, Instant now) {
    this.status = EmailNotificationStatus.SENT;
    this.provider = provider;
    this.providerMessageId = providerMessageId;
    this.sentAt = now;
    this.lastError = null;
    this.updatedAt = now;
  }

  public void markRetry(String error, Instant nextAttemptAt, Instant now) {
    this.status = EmailNotificationStatus.PENDING;
    this.attemptCount++;
    this.lastError = truncate(error);
    this.nextAttemptAt = nextAttemptAt;
    this.updatedAt = now;
  }

  public void markFailed(String error, Instant now) {
    this.status = EmailNotificationStatus.FAILED;
    this.attemptCount++;
    this.lastError = truncate(error);
    this.failedAt = now;
    this.nextAttemptAt = null;
    this.updatedAt = now;
  }

  private String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 1000 ? value : value.substring(0, 1000);
  }

  public UUID getId() {
    return id;
  }

  public EmailNotificationType getType() {
    return type;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getSubject() {
    return subject;
  }

  public String getBodyText() {
    return bodyText;
  }

  public String getBodyHtml() {
    return bodyHtml;
  }

  public EmailNotificationStatus getStatus() {
    return status;
  }

  public String getProvider() {
    return provider;
  }

  public String getProviderMessageId() {
    return providerMessageId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getFailedAt() {
    return failedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
