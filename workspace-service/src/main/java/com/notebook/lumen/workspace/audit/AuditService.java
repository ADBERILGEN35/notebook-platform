package com.notebook.lumen.workspace.audit;

import com.notebook.lumen.common.security.sanitization.SensitiveDataSanitizer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final AuditEventRepository repository;

  public AuditService(AuditEventRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      String eventType,
      UUID actorUserId,
      UUID workspaceId,
      String aggregateType,
      UUID aggregateId,
      Map<String, Object> metadata) {
    try {
      repository.save(
          new AuditEvent(
              UUID.randomUUID(),
              eventType,
              actorUserId,
              workspaceId,
              aggregateType,
              aggregateId,
              MDC.get("requestId"),
              null,
              null,
              SensitiveDataSanitizer.sanitizeMetadata(metadata),
              Instant.now()));
    } catch (RuntimeException e) {
      log.error(
          "Audit event write failed eventType={} aggregateType={}", eventType, aggregateType, e);
    }
  }
}
