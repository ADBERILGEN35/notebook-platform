package com.notebook.lumen.identity.scim.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocRequest;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaMultiPageRemoteFetcher;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderRequestBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScimDeltaFetchDiagnosticsServiceTest {

  @Test
  void remoteFetchDisabledByDefault() {
    var service = newService(properties(false));
    var readiness = service.evaluateReadiness(List.of());
    assertThat(readiness.remoteFetchEnabled()).isFalse();
    assertThat(readiness.warnings())
        .contains(ScimProviderResponseClassifier.WARNING_REMOTE_FETCH_DISABLED);
  }

  @Test
  void simulated429ProducesRateLimitedWithoutRawHeaderInSummary() {
    var service = newService(properties(false));
    var diag =
        service.evaluateDryRun(
            new DryRunPocRequest(ScimResourceType.USER, false, null, 429, "120", false, false),
            List.of());

    assertThat(diag.providerErrorClass()).isEqualTo(ScimProviderErrorClass.RATE_LIMITED);
    assertThat(diag.retryAfterObserved()).isTrue();
    assertThat(diag.retryAfterSeconds()).isEqualTo(120);
    String summary = service.runErrorSummary(diag);
    assertThat(summary).contains("retryAfterSeconds=120");
    assertThat(summary).doesNotContain("Bearer").doesNotContain("120\r\n");
  }

  @Test
  void dryRunSummaryDoesNotContainToken() {
    var service = newService(properties(false));
    var diag =
        service.evaluateDryRun(
            new DryRunPocRequest(ScimResourceType.USER, false, null, 503, null, false, false),
            List.of());
    String encoded = service.runErrorSummary(diag);
    assertThat(encoded).doesNotContain("secret");
    assertThat(service.runErrorCode(diag)).isEqualTo("PROVIDER_UNAVAILABLE");
  }

  private static ScimDeltaFetchDiagnosticsService newService(ScimProperties properties) {
    return new ScimDeltaFetchDiagnosticsService(
        properties,
        mock(ScimDeltaMultiPageRemoteFetcher.class),
        new ScimDeltaProviderRequestBuilder());
  }

  private static ScimProperties properties(boolean remoteFetch) {
    return new ScimProperties(
        true,
        "secret",
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
        true,
        true,
        remoteFetch,
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
        0);
  }
}
