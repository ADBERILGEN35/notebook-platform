package com.notebook.lumen.identity.scim.sync.delta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimProviderErrorClass;
import com.notebook.lumen.identity.scim.sync.ScimResourceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScimDeltaMultiPageRemoteFetcherTest {

  @Test
  void defaultMultiPageDisabledPerformsSingleGet() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    when(client.fetch(any(), anyString()))
        .thenReturn(
            pageResult(5, true, Optional.of(ScimDeltaPaginationContinuation.startIndex(6))));

    var fetcher = newFetcher(client, properties(false, 1, 500));
    var diag = fetcher.fetchPages(ScimResourceType.USER, "token", List.of());

    verify(client, times(1)).fetch(any(), anyString());
    assertThat(diag.pagesObserved()).isEqualTo(1);
    assertThat(diag.fetchedResourceCount()).isEqualTo(5);
    assertThat(diag.stoppedReason()).isEqualTo(ScimDeltaStoppedReason.SINGLE_PAGE_ONLY.name());
    assertThat(diag.warnings()).contains(ScimDeltaRemoteFetchWarnings.MULTI_PAGE_DISABLED);
  }

  @Test
  void multiPageFollowsSanitizedCursorsUpToMaxPages() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    when(client.fetch(any(), anyString()))
        .thenReturn(
            pageResult(2, true, Optional.of(ScimDeltaPaginationContinuation.startIndex(3))),
            pageResult(3, true, Optional.of(ScimDeltaPaginationContinuation.startIndex(6))),
            pageResult(1, false, Optional.empty()));

    var fetcher = newFetcher(client, properties(true, 3, 500));
    var diag = fetcher.fetchPages(ScimResourceType.USER, "token", List.of());

    verify(client, times(3)).fetch(any(), anyString());
    assertThat(diag.pagesObserved()).isEqualTo(3);
    assertThat(diag.fetchedResourceCount()).isEqualTo(6);
    assertThat(diag.stoppedReason()).isEqualTo(ScimDeltaStoppedReason.PAGE_LIMIT_REACHED.name());
    assertThat(diag.pageLimitReached()).isTrue();
    assertThat(diag.warnings()).contains(ScimDeltaRemoteFetchWarnings.PAGE_LIMIT_REACHED);
  }

  @Test
  void stopsWhenNoNextCursor() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    when(client.fetch(any(), anyString())).thenReturn(pageResult(4, false, Optional.empty()));

    var fetcher = newFetcher(client, properties(true, 5, 500));
    var diag = fetcher.fetchPages(ScimResourceType.USER, "token", List.of());

    verify(client, times(1)).fetch(any(), anyString());
    assertThat(diag.stoppedReason()).isEqualTo(ScimDeltaStoppedReason.NO_NEXT_CURSOR.name());
  }

  @Test
  void rateLimitedStopsLoopWithoutLeakingCursorInDiagnostics() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    when(client.fetch(any(), anyString()))
        .thenReturn(
            pageResult(
                2, true, Optional.of(ScimDeltaPaginationContinuation.skipToken("secret-cursor"))),
            rateLimitedPage());

    var fetcher = newFetcher(client, properties(true, 5, 500));
    var diag = fetcher.fetchPages(ScimResourceType.USER, "token", List.of());

    verify(client, times(2)).fetch(any(), anyString());
    assertThat(diag.stoppedReason()).isEqualTo(ScimDeltaStoppedReason.PROVIDER_RATE_LIMITED.name());
    assertThat(diag.providerErrorClass()).isEqualTo(ScimProviderErrorClass.RATE_LIMITED);
    assertThat(diag.toString()).doesNotContain("secret-cursor");
    assertThat(diag.warnings()).contains(ScimDeltaRemoteFetchWarnings.RATE_LIMIT_STOPPED);
  }

  @Test
  void resourceLimitReachedStopsLoop() {
    ScimDeltaProviderClient client = mock(ScimDeltaProviderClient.class);
    when(client.fetch(any(), anyString()))
        .thenReturn(
            pageResult(300, true, Optional.of(ScimDeltaPaginationContinuation.startIndex(301))),
            pageResult(300, true, Optional.of(ScimDeltaPaginationContinuation.startIndex(601))));

    var fetcher = newFetcher(client, properties(true, 10, 500));
    var diag = fetcher.fetchPages(ScimResourceType.USER, "token", List.of());

    assertThat(diag.resourceLimitReached()).isTrue();
    assertThat(diag.stoppedReason())
        .isEqualTo(ScimDeltaStoppedReason.RESOURCE_LIMIT_REACHED.name());
    assertThat(diag.fetchedResourceCount()).isGreaterThanOrEqualTo(500);
  }

  private static ScimDeltaProviderFetchResult pageResult(
      int count, boolean nextCursor, Optional<ScimDeltaPaginationContinuation> continuation) {
    return new ScimDeltaProviderFetchResult(
        true,
        true,
        200,
        count,
        1,
        nextCursor,
        true,
        false,
        null,
        false,
        null,
        ScimProviderErrorClass.NONE,
        List.of(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_ATTEMPTED),
        continuation);
  }

  private static ScimDeltaProviderFetchResult rateLimitedPage() {
    return new ScimDeltaProviderFetchResult(
        true,
        true,
        429,
        0,
        1,
        false,
        false,
        true,
        60,
        false,
        null,
        ScimProviderErrorClass.RATE_LIMITED,
        List.of(),
        Optional.empty());
  }

  private static ScimDeltaMultiPageRemoteFetcher newFetcher(
      ScimDeltaProviderClient client, ScimProperties properties) {
    ScimDeltaProviderRequestBuilder builder = mock(ScimDeltaProviderRequestBuilder.class);
    when(builder.buildDiagnosticPage(any(), any()))
        .thenReturn(
            Optional.of(
                new ScimDeltaProviderRequest(
                    ScimDeltaHttpMethod.GET,
                    "https://idp.example.com/scim/v2/Users?count=10",
                    ScimResourceType.USER,
                    10)));
    return new ScimDeltaMultiPageRemoteFetcher(properties, client, builder);
  }

  private static ScimProperties properties(boolean multiPage, int maxPages, int maxResources) {
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
        "generic",
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
        true,
        3000,
        300,
        30,
        "https://idp.example.com/scim/v2",
        "secret-name",
        "secret-key",
        "bearer",
        100,
        multiPage,
        maxPages,
        maxResources,
        0);
  }
}
