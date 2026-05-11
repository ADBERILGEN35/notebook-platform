package com.notebook.lumen.identity.siem.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.siem.SiemProperties;
import com.notebook.lumen.identity.siem.domain.SiemEventOutbox;
import com.notebook.lumen.identity.siem.domain.SiemOutboxStatus;
import com.notebook.lumen.identity.siem.infrastructure.SiemEventOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SiemOutboxWorkerTest {

  @Test
  void marksDeadWhenNonRetryableFailure() {
    SiemProperties properties =
        new SiemProperties(
            true, "log", "", "none", "", "", "", 10, 100, 1, 30, 3600, true, 30, 30, 90, false);
    SiemOutboxService outboxService = mock(SiemOutboxService.class);
    SiemPublisherResolver resolver = mock(SiemPublisherResolver.class);
    SiemEventPublisher publisher = mock(SiemEventPublisher.class);
    SiemEventOutboxRepository repository = mock(SiemEventOutboxRepository.class);
    when(resolver.resolve()).thenReturn(publisher);
    when(publisher.publish(org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(SiemPublishResult.nonRetryableFailure("bad request"));

    SiemEventOutbox item =
        new SiemEventOutbox(
            UUID.randomUUID(),
            "USER_LOGIN_FAILED",
            "AUTH_SECURITY",
            "HIGH",
            "identity-service",
            null,
            null,
            null,
            "req-1",
            Map.of(
                "id",
                UUID.randomUUID(),
                "timestamp",
                Instant.now(),
                "sourceService",
                "identity-service",
                "environment",
                "test",
                "eventType",
                "USER_LOGIN_FAILED",
                "category",
                "AUTH_SECURITY",
                "severity",
                "HIGH",
                "schemaVersion",
                1),
            SiemOutboxStatus.SENDING,
            0,
            Instant.now(),
            Instant.now(),
            "instance",
            null,
            Instant.now(),
            null);
    when(outboxService.claimDueEvents(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(List.of(item));
    when(repository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(item));

    SiemOutboxWorker worker = new SiemOutboxWorker(properties, outboxService, resolver, repository);
    worker.pollAndPublish();
    verify(repository).saveAll(org.mockito.ArgumentMatchers.anyList());
  }
}
