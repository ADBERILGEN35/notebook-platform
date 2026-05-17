package com.notebook.lumen.identity.scim.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DiagnosticRunRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ScimSyncDiagnosticsServiceTest {

  @Test
  void compatibilityStatusExposesCapabilitiesWithoutToken() {
    ScimSyncCheckpointRepository checkpointRepository = mock(ScimSyncCheckpointRepository.class);
    ScimSyncRunRepository runRepository = mock(ScimSyncRunRepository.class);
    AuditService auditService = mock(AuditService.class);
    when(checkpointRepository.findAllByOrderByProviderAscResourceTypeAsc()).thenReturn(List.of());
    when(runRepository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    ScimSyncDiagnosticsService service =
        new ScimSyncDiagnosticsService(
            new ScimProperties(
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
                true,
                true,
                true,
                false,
                true,
                200,
                false,
                true,
                false,
                3000,
                300,
                30,
                "",
                "",
                "",
                "",
                100,
                false,
                1,
                500,
                0),
            checkpointRepository,
            runRepository,
            auditService,
            new SimpleMeterRegistry());

    var status = service.compatibilityStatus(mock(HttpServletRequest.class));

    assertThat(status.scimProvider().type()).isEqualTo("okta");
    assertThat(status.scimProvider().deltaSyncEnabled()).isFalse();
    assertThat(status.scimProvider().bulkSupported()).isTrue();
    assertThat(status.toString()).doesNotContain("secret-token");
    verify(auditService)
        .record(
            eq("SCIM_COMPATIBILITY_STATUS_VIEWED"),
            any(),
            eq("SCIM_DIAGNOSTICS"),
            any(),
            any(),
            any());
  }

  @Test
  void ensureCheckpointCreatesAndUpdatesCheckpoint() {
    ScimSyncCheckpointRepository checkpointRepository = mock(ScimSyncCheckpointRepository.class);
    ScimSyncRunRepository runRepository = mock(ScimSyncRunRepository.class);
    when(checkpointRepository.findByProviderAndResourceType("generic", ScimResourceType.USER))
        .thenReturn(Optional.empty());
    when(checkpointRepository.save(any(ScimSyncCheckpoint.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ScimSyncDiagnosticsService service =
        newService(checkpointRepository, runRepository, mock(AuditService.class));

    var checkpoint =
        service.ensureCheckpoint(
            ScimResourceType.USER, ScimSyncMode.DELTA, "cursor-1", mock(HttpServletRequest.class));

    assertThat(checkpoint.resourceType()).isEqualTo(ScimResourceType.USER);
    assertThat(checkpoint.syncMode()).isEqualTo(ScimSyncMode.DELTA);
    assertThat(checkpoint.checkpointPresent()).isTrue();
    assertThat(checkpoint.status()).isEqualTo(ScimSyncCheckpointStatus.IDLE);
    verify(checkpointRepository).save(any(ScimSyncCheckpoint.class));
  }

  @Test
  void createDiagnosticRunRecordsCompletionAndFailureWithoutPayload() {
    ScimSyncCheckpointRepository checkpointRepository = mock(ScimSyncCheckpointRepository.class);
    ScimSyncRunRepository runRepository = mock(ScimSyncRunRepository.class);
    when(runRepository.save(any(ScimSyncRun.class))).thenAnswer(inv -> inv.getArgument(0));
    AuditService auditService = mock(AuditService.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ScimSyncDiagnosticsService service =
        new ScimSyncDiagnosticsService(
            ScimProperties.withLegacyDefaults(
                true, "token", "", true, "notebook-admins", true, 5, false, 100, 10),
            checkpointRepository,
            runRepository,
            auditService,
            meterRegistry);

    var completed =
        service.createDiagnosticRun(
            new DiagnosticRunRequest(
                ScimResourceType.GROUP,
                ScimSyncMode.FULL,
                Map.of("processed", 3L, "created", 1L, "updated", 2L),
                null,
                null),
            request("req-1"));
    var failed =
        service.createDiagnosticRun(
            new DiagnosticRunRequest(
                ScimResourceType.USER, ScimSyncMode.DELTA, null, "RATE_LIMITED", "retry later"),
            request("req-2"));

    assertThat(completed.status()).isEqualTo(ScimSyncRunStatus.COMPLETED);
    assertThat(completed.processedCount()).isEqualTo(3);
    assertThat(failed.status()).isEqualTo(ScimSyncRunStatus.FAILED);
    assertThat(failed.lastErrorCode()).isEqualTo("RATE_LIMITED");
    assertThat(completed.toString()).doesNotContain("token");
    assertThat(meterRegistry.find("scim_sync_runs_total").counter()).isNotNull();
    assertThat(
            meterRegistry
                .find("scim_sync_processed_total")
                .tag("resourceType", "GROUP")
                .tag("result", "processed")
                .counter())
        .isNotNull();
    assertThat(
            meterRegistry.find("scim_sync_errors_total").tag("errorCode", "RATE_LIMITED").counter())
        .isNotNull();
    verify(auditService)
        .record(
            eq("SCIM_SYNC_RUN_COMPLETED"),
            any(),
            eq("SCIM_SYNC_RUN"),
            any(UUID.class),
            any(),
            any());
    verify(auditService)
        .record(
            eq("SCIM_SYNC_RUN_FAILED"), any(), eq("SCIM_SYNC_RUN"), any(UUID.class), any(), any());
  }

  private static ScimSyncDiagnosticsService newService(
      ScimSyncCheckpointRepository checkpointRepository,
      ScimSyncRunRepository runRepository,
      AuditService auditService) {
    return new ScimSyncDiagnosticsService(
        ScimProperties.withLegacyDefaults(
            true, "token", "", true, "notebook-admins", true, 5, false, 100, 10),
        checkpointRepository,
        runRepository,
        auditService,
        new SimpleMeterRegistry());
  }

  private static HttpServletRequest request(String requestId) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getAttribute("requestId")).thenReturn(requestId);
    when(request.getHeader("X-Request-Id")).thenReturn(requestId);
    return request;
  }
}
