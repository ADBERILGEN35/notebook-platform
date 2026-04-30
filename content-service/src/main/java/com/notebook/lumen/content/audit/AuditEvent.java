package com.notebook.lumen.content.audit;

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
@Table(name = "content_audit_events")
public class AuditEvent {
  @Id private UUID id;
  private String eventType;
  private UUID actorUserId;
  private UUID workspaceId;
  private String aggregateType;
  private UUID aggregateId;
  private String requestId;
  private String ipAddress;
  private String userAgent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  private Instant createdAt;

  protected AuditEvent() {}

  public AuditEvent(
      UUID id,
      String eventType,
      UUID actorUserId,
      UUID workspaceId,
      String aggregateType,
      UUID aggregateId,
      String requestId,
      String ipAddress,
      String userAgent,
      Map<String, Object> metadata,
      Instant createdAt) {
    this.id = id;
    this.eventType = eventType;
    this.actorUserId = actorUserId;
    this.workspaceId = workspaceId;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.requestId = requestId;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.metadata = metadata;
    this.createdAt = createdAt;
  }
}
