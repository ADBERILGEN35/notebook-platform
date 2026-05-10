package com.notebook.lumen.notification.admin.deadletter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_dead_letter_requeue_requests")
public class DeadLetterRequeueRequestEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "source", nullable = false, length = 32)
  private String source;

  @Column(name = "dead_letter_id", nullable = false)
  private UUID deadLetterId;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "requested_by_user_id", nullable = false, length = 128)
  private String requestedByUserId;

  @Column(name = "result_status", nullable = false, length = 32)
  private String resultStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected DeadLetterRequeueRequestEntity() {}

  public DeadLetterRequeueRequestEntity(
      UUID id,
      String source,
      UUID deadLetterId,
      String idempotencyKey,
      String requestedByUserId,
      String resultStatus,
      Instant createdAt) {
    this.id = id;
    this.source = source;
    this.deadLetterId = deadLetterId;
    this.idempotencyKey = idempotencyKey;
    this.requestedByUserId = requestedByUserId;
    this.resultStatus = resultStatus;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getSource() {
    return source;
  }

  public UUID getDeadLetterId() {
    return deadLetterId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestedByUserId() {
    return requestedByUserId;
  }

  public String getResultStatus() {
    return resultStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
