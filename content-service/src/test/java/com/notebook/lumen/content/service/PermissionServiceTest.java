package com.notebook.lumen.content.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.notebook.lumen.content.client.WorkspaceClient;
import com.notebook.lumen.content.shared.exception.ContentException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PermissionServiceTest {
  @Test
  void permissionClientUnavailable_failsClosedWith503() {
    WorkspaceClient client = Mockito.mock(WorkspaceClient.class);
    UUID userId = UUID.randomUUID();
    UUID notebookId = UUID.randomUUID();
    when(client.notebookPermissions(notebookId, userId))
        .thenThrow(new IllegalStateException("down"));
    PermissionService service =
        new PermissionService(client, CircuitBreaker.ofDefaults("test"), Retry.ofDefaults("test"));

    assertThatThrownBy(() -> service.requireReadable(userId, notebookId))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("WORKSPACE_SERVICE_UNAVAILABLE");
  }
}
