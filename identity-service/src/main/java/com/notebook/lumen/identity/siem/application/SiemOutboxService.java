package com.notebook.lumen.identity.siem.application;

import com.notebook.lumen.identity.audit.AuditEvent;
import com.notebook.lumen.identity.siem.SiemProperties;
import com.notebook.lumen.identity.siem.domain.SiemEventOutbox;
import com.notebook.lumen.identity.siem.domain.SiemOutboxStatus;
import com.notebook.lumen.identity.siem.infrastructure.SiemEventOutboxRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiemOutboxService {
  private final SiemProperties properties;
  private final SiemEventOutboxRepository repository;
  private final SiemEventClassifier classifier;
  private final String environment;
  private final String sourceService;

  public SiemOutboxService(
      SiemProperties properties,
      SiemEventOutboxRepository repository,
      SiemEventClassifier classifier,
      @Value("${spring.profiles.active:default}") String environment,
      @Value("${spring.application.name:identity-service}") String sourceService) {
    this.properties = properties;
    this.repository = repository;
    this.classifier = classifier;
    this.environment = environment;
    this.sourceService = sourceService;
  }

  @Transactional
  public void enqueueFromAuditEvent(AuditEvent auditEvent, HttpServletRequest request) {
    if (!properties.pushEnabled()) {
      return;
    }
    var classified = classifier.classify(auditEvent.getEventType());
    if (classified.isEmpty()) {
      return;
    }
    Map<String, Object> payload = payload(auditEvent, classified.get(), request);
    SiemEventOutbox outbox =
        new SiemEventOutbox(
            UUID.randomUUID(),
            auditEvent.getEventType(),
            classified.get().category(),
            classified.get().severity(),
            sourceService,
            auditEvent.getAggregateType() != null
                    && "USER".equalsIgnoreCase(auditEvent.getAggregateType())
                ? auditEvent.getAggregateId()
                : null,
            auditEvent.getActorUserId(),
            auditEvent.getWorkspaceId(),
            auditEvent.getRequestId(),
            payload,
            SiemOutboxStatus.PENDING,
            0,
            Instant.now(),
            null,
            null,
            null,
            Instant.now(),
            null);
    repository.save(outbox);
  }

  @Transactional
  public List<SiemEventOutbox> claimDueEvents(String instanceId) {
    List<SiemEventOutbox> due =
        repository.lockDueEvents(
            SiemOutboxStatus.PENDING.name(), Instant.now(), properties.batchSize());
    Instant now = Instant.now();
    for (SiemEventOutbox item : due) {
      item.markSending(instanceId, now);
    }
    return repository.saveAll(due);
  }

  private Map<String, Object> payload(
      AuditEvent event,
      SiemEventClassifier.ClassifiedEvent classified,
      HttpServletRequest request) {
    Map<String, Object> metadata = event.getMetadata() == null ? Map.of() : event.getMetadata();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", event.getId());
    payload.put("timestamp", event.getCreatedAt());
    payload.put("sourceService", sourceService);
    payload.put("environment", environment);
    payload.put("eventType", event.getEventType());
    payload.put("category", classified.category());
    payload.put("severity", classified.severity());
    payload.put("actorUserId", event.getActorUserId());
    payload.put(
        "subjectUserId",
        "USER".equalsIgnoreCase(event.getAggregateType()) ? event.getAggregateId() : null);
    payload.put("workspaceId", event.getWorkspaceId());
    payload.put("requestId", event.getRequestId());
    payload.put("ipAddress", event.getIpAddress());
    payload.put("userAgent", event.getUserAgent());
    payload.put("metadata", metadata);
    payload.put("schemaVersion", 1);
    return payload;
  }
}
