package com.notebook.lumen.identity.scim.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocRequest;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaMultiPageRemoteFetcher;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderFetchResult;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderRequestBuilder;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaRemoteFetchWarnings;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaStoppedReason;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScimDeltaRemoteFetchDiagnosticsTest {

  @Test
  void disabledDefaultDoesNotInvokeFetcher() {
    ScimDeltaMultiPageRemoteFetcher fetcher = mock(ScimDeltaMultiPageRemoteFetcher.class);
    var service =
        new ScimDeltaFetchDiagnosticsService(
            properties(false, "", ""), fetcher, new ScimDeltaProviderRequestBuilder());

    var diag = service.evaluateDryRun(null, List.of());

    verify(fetcher, never()).fetchPages(any(), anyString(), anyList());
    assertThat(diag.warnings())
        .contains(ScimProviderResponseClassifier.WARNING_REMOTE_FETCH_DISABLED);
  }

  @Test
  void enabledButMissingConfigDoesNotInvokeFetcher() {
    ScimDeltaMultiPageRemoteFetcher fetcher = mock(ScimDeltaMultiPageRemoteFetcher.class);
    var service =
        new ScimDeltaFetchDiagnosticsService(
            properties(true, "https://idp.example.com/scim/v2", ""),
            fetcher,
            new ScimDeltaProviderRequestBuilder());

    var diag = service.evaluateDryRun(null, List.of());

    verify(fetcher, never()).fetchPages(any(), anyString(), anyList());
    assertThat(diag.warnings()).contains(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_NOT_CONFIGURED);
  }

  @Test
  void enabledWithConfigUsesMultiPageFetcher() {
    ScimDeltaMultiPageRemoteFetcher fetcher = mock(ScimDeltaMultiPageRemoteFetcher.class);
    ScimProperties props = properties(true, "https://idp.example.com/scim/v2", "bearer-secret");
    when(fetcher.fetchPages(any(), anyString(), anyList()))
        .thenReturn(
            ScimDeltaRateLimitDiagnostics.fromMultiPage(
                props,
                List.of(),
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
                    List.of(),
                    Optional.empty()),
                1,
                4,
                false,
                ScimDeltaStoppedReason.SINGLE_PAGE_ONLY,
                false,
                false));

    var service =
        new ScimDeltaFetchDiagnosticsService(props, fetcher, new ScimDeltaProviderRequestBuilder());

    var diag = service.evaluateDryRun(null, List.of());

    verify(fetcher).fetchPages(any(), anyString(), anyList());
    assertThat(diag.fetchedResourceCount()).isEqualTo(4);
    String summary = service.runErrorSummary(diag);
    assertThat(summary).contains("fetchedResourceCount=4");
    assertThat(summary).doesNotContain("bearer").doesNotContain("secret");
  }

  @Test
  void simulationTakesPrecedenceOverRemote() {
    ScimDeltaMultiPageRemoteFetcher fetcher = mock(ScimDeltaMultiPageRemoteFetcher.class);
    var service =
        new ScimDeltaFetchDiagnosticsService(
            properties(true, "https://idp.example.com/scim/v2", "token"),
            fetcher,
            new ScimDeltaProviderRequestBuilder());

    var diag =
        service.evaluateDryRun(
            new DryRunPocRequest(ScimResourceType.USER, false, null, 429, "30", false, false),
            List.of());

    verify(fetcher, never()).fetchPages(any(), anyString(), anyList());
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
        100,
        false,
        1,
        500,
        0);
  }
}
