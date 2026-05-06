package com.notebook.lumen.notification.audit;

import com.notebook.lumen.common.security.sanitization.SensitiveDataSanitizer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
      String eventType, String aggregateType, UUID aggregateId, Map<String, ?> metadata) {
    try {
      repository.save(
          new AuditEvent(
              UUID.randomUUID(),
              eventType,
              aggregateType,
              aggregateId,
              null,
              sanitize(metadata),
              Instant.now()));
    } catch (RuntimeException e) {
      log.warn("Notification audit write failed eventType={}", eventType);
    }
  }

  private Map<String, Object> sanitize(Map<String, ?> metadata) {
    return SensitiveDataSanitizer.sanitizeMetadata(
        metadata.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right)));
  }
}
