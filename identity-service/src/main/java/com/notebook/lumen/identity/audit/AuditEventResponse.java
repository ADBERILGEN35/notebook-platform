package com.notebook.lumen.identity.audit;

import com.notebook.lumen.common.security.sanitization.SensitiveDataSanitizer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
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
  static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.getId(),
        event.getEventType(),
        event.getActorUserId(),
        event.getWorkspaceId(),
        event.getAggregateType(),
        event.getAggregateId(),
        event.getRequestId(),
        event.getIpAddress(),
        event.getUserAgent(),
        SensitiveDataSanitizer.sanitizeMetadata(event.getMetadata()),
        event.getCreatedAt());
  }
}
