package com.notebook.lumen.notification.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_audit_events")
public class AuditEvent {
  @Id private UUID id;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "aggregate_type")
  private String aggregateType;

  @Column(name = "aggregate_id")
  private UUID aggregateId;

  @Column(name = "request_id")
  private String requestId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuditEvent() {}

  public AuditEvent(
      UUID id,
      String eventType,
      String aggregateType,
      UUID aggregateId,
      String requestId,
      Map<String, Object> metadata,
      Instant createdAt) {
    this.id = id;
    this.eventType = eventType;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.requestId = requestId;
    this.metadata = metadata;
    this.createdAt = createdAt;
  }
}
