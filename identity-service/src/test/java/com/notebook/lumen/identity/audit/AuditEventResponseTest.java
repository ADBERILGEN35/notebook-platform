package com.notebook.lumen.identity.audit;

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
            "REFRESH_TOKEN_REVOKED",
            UUID.randomUUID(),
            null,
            "REFRESH_TOKEN",
            UUID.randomUUID(),
            "request-id",
            "203.0.113.10",
            "JUnit",
            Map.of("refreshToken", "secret-token", "reason", "logout"),
            Instant.now());

    AuditEventResponse response = AuditEventResponse.from(event);

    assertThat(response.metadata()).containsEntry("refreshToken", "****");
    assertThat(response.metadata()).containsEntry("reason", "logout");
  }
}
