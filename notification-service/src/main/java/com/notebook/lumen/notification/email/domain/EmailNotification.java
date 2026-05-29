package com.notebook.lumen.notification.email.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  @Enumerated(EnumType.STRING)
  private EmailDeliveryStatus deliveryStatus;

  private Instant deliveredAt;
  private Instant bouncedAt;
  private Instant complainedAt;
  private Instant suppressedAt;
  private String providerEventId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String providerEventPayload;

  private String idempotencyKey;
  private int attemptCount;
  private Instant nextAttemptAt;
  private String lastError;
  private Instant sentAt;
  private Instant failedAt;
  private String lockedBy;
  private Instant lockExpiresAt;
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
      Instant now,
      Instant nextAttemptAt) {
    this.id = id;
    this.type = type;
    this.recipientEmail = recipientEmail;
    this.subject = subject;
    this.bodyText = bodyText;
    this.bodyHtml = bodyHtml;
    this.status = EmailNotificationStatus.PENDING;
    this.deliveryStatus = EmailDeliveryStatus.UNKNOWN;
    this.idempotencyKey = idempotencyKey;
    this.attemptCount = 0;
    this.nextAttemptAt = nextAttemptAt == null ? now : nextAttemptAt;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public EmailNotification(
      UUID id,
      EmailNotificationType type,
      String recipientEmail,
      String subject,
      String bodyText,
      String bodyHtml,
      String idempotencyKey,
      Instant nextAttemptAt) {
    Instant now = Instant.now();
    this.id = id;
    this.type = type;
    this.recipientEmail = recipientEmail;
    this.subject = subject;
    this.bodyText = bodyText;
    this.bodyHtml = bodyHtml;
    this.status = EmailNotificationStatus.PENDING;
    this.deliveryStatus = EmailDeliveryStatus.UNKNOWN;
    this.idempotencyKey = idempotencyKey;
    this.attemptCount = 0;
    this.nextAttemptAt = nextAttemptAt == null ? now : nextAttemptAt;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void markSending(String lockedBy, Instant now, Instant lockExpiresAt) {
    this.status = EmailNotificationStatus.SENDING;
    this.lockedBy = lockedBy;
    this.lockExpiresAt = lockExpiresAt;
    this.updatedAt = now;
  }

  public void markSent(String provider, String providerMessageId, Instant now) {
    this.status = EmailNotificationStatus.SENT;
    this.provider = provider;
    this.providerMessageId = providerMessageId;
    this.deliveryStatus = EmailDeliveryStatus.ACCEPTED;
    this.sentAt = now;
    this.lastError = null;
    clearLock();
    this.updatedAt = now;
  }

  public void markRetry(String error, Instant nextAttemptAt, Instant now) {
    this.status = EmailNotificationStatus.PENDING;
    this.attemptCount++;
    this.lastError = truncate(error);
    this.nextAttemptAt = nextAttemptAt;
    clearLock();
    this.updatedAt = now;
  }

  public void markFailed(String error, Instant now) {
    this.status = EmailNotificationStatus.FAILED;
    this.attemptCount++;
    this.lastError = truncate(error);
    this.failedAt = now;
    this.nextAttemptAt = null;
    clearLock();
    this.updatedAt = now;
  }

  public void markSuppressed(String provider, String providerEventId, Instant now, String payload) {
    this.status = EmailNotificationStatus.CANCELLED;
    this.provider = provider;
    this.providerEventId = providerEventId;
    this.providerEventPayload = truncate(payload);
    this.deliveryStatus = EmailDeliveryStatus.SUPPRESSED;
    this.suppressedAt = now;
    this.nextAttemptAt = null;
    clearLock();
    this.updatedAt = now;
  }

  public void markDelivered(
      String providerEventId, Instant occurredAt, String payload, Instant now) {
    this.deliveryStatus = EmailDeliveryStatus.DELIVERED;
    this.providerEventId = providerEventId;
    this.providerEventPayload = truncate(payload);
    this.deliveredAt = occurredAt == null ? now : occurredAt;
    this.updatedAt = now;
  }

  public void markBounced(String providerEventId, Instant occurredAt, String payload, Instant now) {
    this.deliveryStatus = EmailDeliveryStatus.BOUNCED;
    this.providerEventId = providerEventId;
    this.providerEventPayload = truncate(payload);
    this.bouncedAt = occurredAt == null ? now : occurredAt;
    this.updatedAt = now;
  }

  public void markComplained(
      String providerEventId, Instant occurredAt, String payload, Instant now) {
    this.deliveryStatus = EmailDeliveryStatus.COMPLAINED;
    this.providerEventId = providerEventId;
    this.providerEventPayload = truncate(payload);
    this.complainedAt = occurredAt == null ? now : occurredAt;
    this.updatedAt = now;
  }

  public void recoverStaleSending(String error, Instant now) {
    this.status = EmailNotificationStatus.PENDING;
    this.lastError = truncate(error);
    this.nextAttemptAt = now;
    clearLock();
    this.updatedAt = now;
  }

  private void clearLock() {
    this.lockedBy = null;
    this.lockExpiresAt = null;
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

  public EmailDeliveryStatus getDeliveryStatus() {
    return deliveryStatus;
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

  public String getLockedBy() {
    return lockedBy;
  }

  public Instant getLockExpiresAt() {
    return lockExpiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
