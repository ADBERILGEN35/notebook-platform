package com.notebook.lumen.identity.siem.domain;

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
@Table(name = "siem_event_outbox")
public class SiemEventOutbox {
  @Id private UUID id;

  private String eventType;
  private String category;
  private String severity;
  private String sourceService;
  private UUID subjectUserId;
  private UUID actorUserId;
  private UUID workspaceId;
  private String requestId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  private SiemOutboxStatus status;

  private int attemptCount;
  private Instant nextAttemptAt;
  private Instant lockedAt;
  private String lockedBy;
  private String lastError;
  private Instant createdAt;
  private Instant sentAt;

  protected SiemEventOutbox() {}

  public SiemEventOutbox(
      UUID id,
      String eventType,
      String category,
      String severity,
      String sourceService,
      UUID subjectUserId,
      UUID actorUserId,
      UUID workspaceId,
      String requestId,
      Map<String, Object> payload,
      SiemOutboxStatus status,
      int attemptCount,
      Instant nextAttemptAt,
      Instant lockedAt,
      String lockedBy,
      String lastError,
      Instant createdAt,
      Instant sentAt) {
    this.id = id;
    this.eventType = eventType;
    this.category = category;
    this.severity = severity;
    this.sourceService = sourceService;
    this.subjectUserId = subjectUserId;
    this.actorUserId = actorUserId;
    this.workspaceId = workspaceId;
    this.requestId = requestId;
    this.payload = payload;
    this.status = status;
    this.attemptCount = attemptCount;
    this.nextAttemptAt = nextAttemptAt;
    this.lockedAt = lockedAt;
    this.lockedBy = lockedBy;
    this.lastError = lastError;
    this.createdAt = createdAt;
    this.sentAt = sentAt;
  }

  public UUID getId() {
    return id;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public void markSending(String locker, Instant now) {
    status = SiemOutboxStatus.SENDING;
    lockedAt = now;
    lockedBy = locker;
  }

  public void markSent(Instant now) {
    status = SiemOutboxStatus.SENT;
    sentAt = now;
    lastError = null;
    lockedAt = null;
    lockedBy = null;
  }

  public void markRetry(Instant nextAttemptAt, String error) {
    attemptCount = attemptCount + 1;
    status = SiemOutboxStatus.PENDING;
    this.nextAttemptAt = nextAttemptAt;
    this.lastError = error;
    this.lockedAt = null;
    this.lockedBy = null;
  }

  public void markDead(String error) {
    attemptCount = attemptCount + 1;
    status = SiemOutboxStatus.DEAD;
    this.lastError = error;
    this.lockedAt = null;
    this.lockedBy = null;
  }
}
