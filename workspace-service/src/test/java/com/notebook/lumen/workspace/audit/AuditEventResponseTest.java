package com.notebook.lumen.workspace.audit;

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
            "WORKSPACE_CREATED",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "WORKSPACE",
            UUID.randomUUID(),
            "request-id",
            "203.0.113.10",
            "JUnit",
            Map.of("internalToken", "secret-token", "role", "OWNER"),
            Instant.now());

    AuditEventResponse response = AuditEventResponse.from(event);

    assertThat(response.metadata()).containsEntry("internalToken", "****");
    assertThat(response.metadata()).containsEntry("role", "OWNER");
  }
}
