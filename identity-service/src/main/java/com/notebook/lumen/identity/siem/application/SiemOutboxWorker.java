package com.notebook.lumen.identity.siem.application;

import com.notebook.lumen.identity.siem.SiemProperties;
import com.notebook.lumen.identity.siem.domain.SiemEventOutbox;
import com.notebook.lumen.identity.siem.infrastructure.SiemEventOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SiemOutboxWorker {
  private static final Logger log = LoggerFactory.getLogger(SiemOutboxWorker.class);

  private final SiemProperties properties;
  private final SiemOutboxService outboxService;
  private final SiemPublisherResolver publisherResolver;
  private final SiemEventOutboxRepository repository;
  private final String instanceId = UUID.randomUUID().toString();

  public SiemOutboxWorker(
      SiemProperties properties,
      SiemOutboxService outboxService,
      SiemPublisherResolver publisherResolver,
      SiemEventOutboxRepository repository) {
    this.properties = properties;
    this.outboxService = outboxService;
    this.publisherResolver = publisherResolver;
    this.repository = repository;
  }

  @Scheduled(fixedDelayString = "${identity.siem.worker-poll-interval-seconds:30}000")
  public void pollAndPublish() {
    if (!properties.pushEnabled() || !properties.workerEnabled()) {
      return;
    }
    if ("noop".equals(properties.effectiveProvider())) {
      return;
    }
    List<SiemEventOutbox> batch = outboxService.claimDueEvents(instanceId);
    if (batch.isEmpty()) {
      return;
    }
    List<SiemEventPayload> payloads = batch.stream().map(this::toPayload).toList();
    SiemPublishResult result = publisherResolver.resolve().publish(payloads);
    persistResult(batch, result);
  }

  @Transactional
  protected void persistResult(List<SiemEventOutbox> batch, SiemPublishResult result) {
    Instant now = Instant.now();
    if (result.successful()) {
      batch.forEach(item -> item.markSent(now));
      repository.saveAll(batch);
      return;
    }
    for (SiemEventOutbox item : batch) {
      if (!result.retryable() || item.getAttemptCount() >= properties.maxAttempts()) {
        item.markDead(result.message());
      } else {
        long backoff =
            Math.min(
                properties.backoffMaxSeconds(),
                (long) properties.backoffBaseSeconds() * (1L << Math.max(0, item.getAttemptCount())));
        item.markRetry(now.plusSeconds(backoff), result.message());
      }
    }
    repository.saveAll(batch);
    log.warn("siem_publish_failed retryable={} message={}", result.retryable(), result.message());
  }

  private SiemEventPayload toPayload(SiemEventOutbox item) {
    var p = item.getPayload();
    return new SiemEventPayload(
        UUID.fromString(String.valueOf(p.get("id"))),
        Instant.parse(String.valueOf(p.get("timestamp"))),
        String.valueOf(p.get("sourceService")),
        String.valueOf(p.get("environment")),
        String.valueOf(p.get("eventType")),
        String.valueOf(p.get("category")),
        String.valueOf(p.get("severity")),
        p.get("actorUserId") == null ? null : UUID.fromString(String.valueOf(p.get("actorUserId"))),
        p.get("subjectUserId") == null ? null : UUID.fromString(String.valueOf(p.get("subjectUserId"))),
        p.get("workspaceId") == null ? null : UUID.fromString(String.valueOf(p.get("workspaceId"))),
        p.get("requestId") == null ? null : String.valueOf(p.get("requestId")),
        p.get("ipAddress") == null ? null : String.valueOf(p.get("ipAddress")),
        p.get("userAgent") == null ? null : String.valueOf(p.get("userAgent")),
        (java.util.Map<String, Object>) p.get("metadata"),
        Integer.parseInt(String.valueOf(p.get("schemaVersion"))));
  }
}
