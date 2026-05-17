package com.notebook.lumen.identity.scim.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocRequest;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaHttpMethod;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderClient;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderFetchResult;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderRequest;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderRequestBuilder;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaRemoteFetchWarnings;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScimDeltaRemoteFetchDiagnosticsTest {

  @Test
  void disabledDefaultDoesNotInvokeClient() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    var service =
        new ScimDeltaFetchDiagnosticsService(
            properties(false, "", ""), client, new ScimDeltaProviderRequestBuilder());

    var diag = service.evaluateDryRun(null, List.of());

    verify(client, never()).fetch(any(), anyString());
    assertThat(diag.warnings()).contains(ScimProviderResponseClassifier.WARNING_REMOTE_FETCH_DISABLED);
  }

  @Test
  void enabledButMissingConfigDoesNotInvokeClient() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    var service =
        new ScimDeltaFetchDiagnosticsService(
            properties(true, "https://idp.example.com/scim/v2", ""), client, new ScimDeltaProviderRequestBuilder());

    var diag = service.evaluateDryRun(null, List.of());

    verify(client, never()).fetch(any(), anyString());
    assertThat(diag.warnings()).contains(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_NOT_CONFIGURED);
  }

  @Test
  void enabledWithConfigIssuesGetOnly() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    when(client.fetch(any(), anyString()))
        .thenReturn(
            new ScimDeltaProviderFetchResult(
                true,
                true,
                200,
                4,
                1,
                false,
                true,
                false,
                null,
                false,
                null,
                ScimProviderErrorClass.NONE,
                List.of(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_ATTEMPTED)));

    var builder = mock(ScimDeltaProviderRequestBuilder.class);
    when(builder.buildDiagnosticPage(any(), any()))
        .thenReturn(
            Optional.of(
                new ScimDeltaProviderRequest(
                    ScimDeltaHttpMethod.GET,
                    "https://idp.example.com/scim/v2/Users?count=10",
                    ScimResourceType.USER,
                    10)));

    var service =
        new ScimDeltaFetchDiagnosticsService(
            properties(true, "https://idp.example.com/scim/v2", "bearer-secret"), client, builder);

    var diag = service.evaluateDryRun(null, List.of());

    verify(client).fetch(any(), anyString());
    assertThat(diag.fetchedResourceCount()).isEqualTo(4);
    assertThat(diag.remoteFetchAttempted()).isTrue();
    String summary = service.runErrorSummary(diag);
    assertThat(summary).contains("fetchedResourceCount=4");
    assertThat(summary).doesNotContain("bearer").doesNotContain("secret");
  }

  @Test
  void simulationTakesPrecedenceOverRemote() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    var service =
        new ScimDeltaFetchDiagnosticsService(
            properties(true, "https://idp.example.com/scim/v2", "token"), client, new ScimDeltaProviderRequestBuilder());

    var diag =
        service.evaluateDryRun(
            new DryRunPocRequest(
                ScimResourceType.USER, false, null, 429, "30", false, false), List.of());

    verify(client, never()).fetch(any(), anyString());
    assertThat(diag.providerErrorClass()).isEqualTo(ScimProviderErrorClass.RATE_LIMITED);
  }

  private static ScimProperties properties(boolean remoteFetch, String baseUrl, String bearer) {
    return new ScimProperties(
        true,
        "inbound",
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
        baseUrl,
        "",
        "",
        bearer,
        100);
  }
}
