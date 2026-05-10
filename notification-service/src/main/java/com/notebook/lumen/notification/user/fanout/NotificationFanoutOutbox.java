package com.notebook.lumen.notification.user.fanout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_fanout_outbox")
public class NotificationFanoutOutbox {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private NotificationFanoutOutboxStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "locked_by")
  private String lockedBy;

  @Column(name = "lock_expires_at")
  private Instant lockExpiresAt;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "requeue_count", nullable = false)
  private int requeueCount;

  @Column(name = "last_requeued_at")
  private Instant lastRequeuedAt;

  @Column(name = "last_requeued_by")
  private String lastRequeuedBy;

  @Column(name = "dead_at")
  private Instant deadAt;

  protected NotificationFanoutOutbox() {}

  public NotificationFanoutOutbox(
      UUID id,
      UUID eventId,
      UUID recipientUserId,
      String eventType,
      Map<String, Object> payload,
      Instant now) {
    this.id = id;
    this.eventId = eventId;
    this.recipientUserId = recipientUserId;
    this.eventType = eventType;
    this.payload = payload;
    this.status = NotificationFanoutOutboxStatus.PENDING;
    this.attemptCount = 0;
    this.nextAttemptAt = now;
    this.createdAt = now;
    this.requeueCount = 0;
  }

  public void markSending(String workerId, Instant now, Instant lockExpiresAt) {
    this.status = NotificationFanoutOutboxStatus.SENDING;
    this.lockedAt = now;
    this.lockedBy = workerId;
    this.lockExpiresAt = lockExpiresAt;
  }

  public void markSent(Instant now) {
    this.status = NotificationFanoutOutboxStatus.SENT;
    this.sentAt = now;
    this.lockedAt = null;
    this.lockedBy = null;
    this.lockExpiresAt = null;
    this.lastError = null;
  }

  public void recoverStaleSending(String reason, Instant now) {
    this.status = NotificationFanoutOutboxStatus.PENDING;
    this.lastError = reason;
    this.lockedAt = null;
    this.lockedBy = null;
    this.lockExpiresAt = null;
    this.nextAttemptAt = now;
  }

  public void markDead(String error, Instant now) {
    this.status = NotificationFanoutOutboxStatus.DEAD;
    this.lastError = error;
    this.lockedAt = null;
    this.lockedBy = null;
    this.lockExpiresAt = null;
    this.deadAt = now;
  }

  /**
   * Admin requeue: move DEAD row back to PENDING for worker pickup. Does not clear {@link #lastError}
   * (operational history).
   */
  public void requeueFromDead(Instant now, String actorUserId) {
    if (this.status != NotificationFanoutOutboxStatus.DEAD) {
      throw new IllegalStateException("requeue requires DEAD status");
    }
    this.status = NotificationFanoutOutboxStatus.PENDING;
    this.nextAttemptAt = now;
    this.requeueCount++;
    this.lastRequeuedAt = now;
    this.lastRequeuedBy = actorUserId;
    this.lockedAt = null;
    this.lockedBy = null;
    this.lockExpiresAt = null;
  }

  public void markRetry(String error, Instant nextAttempt, Instant now, int newAttemptCount) {
    this.status = NotificationFanoutOutboxStatus.PENDING;
    this.attemptCount = newAttemptCount;
    this.lastError = error;
    this.nextAttemptAt = nextAttempt;
    this.lockedAt = null;
    this.lockedBy = null;
    this.lockExpiresAt = null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public String getEventType() {
    return eventType;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public NotificationFanoutOutboxStatus getStatus() {
    return status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }

  public String getLockedBy() {
    return lockedBy;
  }

  public Instant getLockExpiresAt() {
    return lockExpiresAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public int getRequeueCount() {
    return requeueCount;
  }

  public Instant getLastRequeuedAt() {
    return lastRequeuedAt;
  }

  public String getLastRequeuedBy() {
    return lastRequeuedBy;
  }

  public Instant getDeadAt() {
    return deadAt;
  }
}
