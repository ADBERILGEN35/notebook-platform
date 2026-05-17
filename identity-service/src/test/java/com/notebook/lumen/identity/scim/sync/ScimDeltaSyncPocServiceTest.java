package com.notebook.lumen.identity.scim.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocRequest;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.SyncRunResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class ScimDeltaSyncPocServiceTest {

  @Test
  void dryRunDisabledWhenPocFlagOff() {
    ScimDeltaSyncPocService service = newService(pocProperties(false), mock(), mock(), mock());

    assertThatThrownBy(
            () ->
                service.executeDryRun(
                    new DryRunPocRequest(
                        ScimResourceType.USER, false, null, null, null, false, false),
                    null))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void dryRunDoesNotExposeTokenOrPayload() {
    ScimSyncDiagnosticsService diagnostics = mock(ScimSyncDiagnosticsService.class);
    UUID runId = UUID.randomUUID();
    when(diagnostics.createDiagnosticRun(any(), any()))
        .thenReturn(
            completedRun(runId, null, "providerErrorClass=RATE_LIMITED;retryAfterSeconds=30"));
    when(diagnostics.ensureCheckpoint(any(), any(), any(), any()))
        .thenReturn(
            new ScimSyncDiagnosticsDtos.CheckpointResponse(
                UUID.randomUUID(),
                "okta",
                ScimResourceType.USER,
                ScimSyncMode.DELTA,
                false,
                Instant.now(),
                Instant.now(),
                ScimSyncCheckpointStatus.IDLE,
                Instant.now()));

    ScimDeltaSyncPocService service =
        newService(pocProperties(true), mock(ScimSyncCheckpointRepository.class), mock(), diagnostics);

    var response =
        service.executeDryRun(
            new DryRunPocRequest(ScimResourceType.USER, true, 30, null, null, false, false),
            mock(HttpServletRequest.class));

    assertThat(response.run().deprovisionedCount()).isZero();
    assertThat(response.remoteFetchEnabled()).isFalse();
    assertThat(response.warnings()).contains(ScimProviderResponseClassifier.WARNING_REMOTE_FETCH_DISABLED);
    assertThat(response.toString()).doesNotContain("token");
    assertThat(response.toString()).doesNotContain("Bearer");
    verify(diagnostics).createDiagnosticRun(any(), any());
  }

  @Test
  void readinessIncludesStrategyWithoutSecrets() {
    ScimSyncCheckpointRepository checkpoints = mock(ScimSyncCheckpointRepository.class);
    ScimSyncRunRepository runs = mock(ScimSyncRunRepository.class);
    when(checkpoints.findByProviderAndResourceType("okta", ScimResourceType.USER))
        .thenReturn(Optional.empty());
    when(runs.search(any(), any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    ScimDeltaSyncPocService service =
        newService(pocProperties(true), checkpoints, runs, mock(ScimSyncDiagnosticsService.class));

    var readiness = service.deltaReadiness(mock(HttpServletRequest.class));

    assertThat(readiness.deltaPocEnabled()).isTrue();
    assertThat(readiness.selectedStrategy()).isEqualTo("LAST_MODIFIED_FILTER");
    assertThat(readiness.remoteFetchEnabled()).isFalse();
    assertThat(readiness.toString()).doesNotContain("secret-token");
  }

  private static SyncRunResponse completedRun(UUID id, String errorCode, String summary) {
    return new SyncRunResponse(
        id,
        "okta",
        ScimResourceType.USER,
        ScimSyncMode.DELTA,
        ScimSyncRunStatus.COMPLETED,
        Instant.now(),
        Instant.now(),
        0,
        0,
        0,
        0,
        1,
        0,
        errorCode,
        summary,
        "req-1",
        errorCode,
        30,
        false,
        Instant.now());
  }

  private static ScimProperties pocProperties(boolean pocEnabled) {
    return new ScimProperties(
        true,
        "secret-token",
        "",
        true,
        "notebook-admins",
        true,
        5,
        false,
        100,
        10,
        "okta",
        false,
        "diagnostic",
        false,
        true,
        true,
        false,
        true,
        100,
        pocEnabled,
        true,
        false,
        3000,
        300,
        30,
        "",
        "",
        "",
        "",
        100);
  }

  private static ScimDeltaSyncPocService newService(
      ScimProperties properties,
      ScimSyncCheckpointRepository checkpoints,
      ScimSyncRunRepository runs,
      ScimSyncDiagnosticsService diagnostics) {
    if (diagnostics == null) {
      diagnostics = mock(ScimSyncDiagnosticsService.class);
    }
    if (checkpoints == null) {
      checkpoints = mock(ScimSyncCheckpointRepository.class);
    }
    if (runs == null) {
      runs = mock(ScimSyncRunRepository.class);
      when(runs.search(any(), any(), any(), any(), any(), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));
    }
    return new ScimDeltaSyncPocService(
        properties,
        new ScimDeltaStrategyResolver(),
        new ScimDeltaFetchDiagnosticsService(
            properties,
            org.mockito.Mockito.mock(
                com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderClient.class),
            new com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderRequestBuilder()),
        checkpoints,
        runs,
        diagnostics,
        mock(AuditService.class));
  }
}
