package com.notebook.lumen.content.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventResponseTest {
  @Test
  void sanitizesSensitiveMetadataBeforeResponse() {
    AuditEvent event =
        new AuditEvent(
            UUID.randomUUID(),
            "NOTE_UPDATED",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "NOTE",
            UUID.randomUUID(),
            "request-id",
            "203.0.113.10",
            "JUnit",
            Map.of("authorization", "Bearer token", "blockCount", 3),
            Instant.now());

    AuditEventResponse response = AuditEventResponse.from(event);

    assertThat(response.metadata()).containsEntry("authorization", "****");
    assertThat(response.metadata()).containsEntry("blockCount", 3);
  }
}
