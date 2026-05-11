package com.notebook.lumen.search.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.search.provider.postgres.PostgreSqlSearchProvider;
import com.notebook.lumen.search.query.dto.PageResponse;
import com.notebook.lumen.search.query.dto.SearchNoteResult;
import com.notebook.lumen.search.shared.config.SearchProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchProviderRouterTest {
  @Test
  void selectedProviderUsesOpenSearchWhenConfigured() {
    SearchProviderRouter router =
        router(
            properties("opensearch", false, false),
            new FakeProvider(SearchProviderType.POSTGRES, false),
            new FakeProvider(SearchProviderType.OPENSEARCH, false));

    assertThat(router.selectedType()).isEqualTo(SearchProviderType.OPENSEARCH);
  }

  @Test
  void searchFallsBackToPostgresOnlyWhenEnabled() {
    SearchProviderRouter router =
        router(
            properties("opensearch", false, true),
            new FakeProvider(SearchProviderType.POSTGRES, false),
            new FakeProvider(SearchProviderType.OPENSEARCH, true));

    PageResponse<SearchNoteResult> response =
        router.search(new SearchQuery(UUID.randomUUID(), null, "roadmap", 0, 10, true));

    assertThat(response.items()).hasSize(1);
  }

  @Test
  void searchDoesNotFallbackWhenDisabled() {
    SearchProviderRouter router =
        router(
            properties("opensearch", false, false),
            new FakeProvider(SearchProviderType.POSTGRES, false),
            new FakeProvider(SearchProviderType.OPENSEARCH, true));

    assertThatThrownBy(
            () -> router.search(new SearchQuery(UUID.randomUUID(), null, "roadmap", 0, 10, true)))
        .isInstanceOf(SearchProviderException.class);
  }

  private SearchProviderRouter router(
      SearchProperties properties, SearchProvider postgres, SearchProvider opensearch) {
    PostgreSqlSearchProvider postgresProvider = mock(PostgreSqlSearchProvider.class);
    when(postgresProvider.search(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> postgres.search(invocation.getArgument(0)));
    return new SearchProviderRouter(
        properties,
        postgresProvider,
        List.of(postgres, opensearch),
        new SearchProviderMetrics(new SimpleMeterRegistry()));
  }

  private SearchProperties properties(String provider, boolean dualWrite, boolean fallback) {
    return new SearchProperties(
        200000,
        120,
        2,
        50,
        "",
        provider,
        dualWrite,
        fallback,
        new SearchProperties.OpenSearch(
            "http://localhost:9200", "", "", "notebook-notes", 1000, 3000, false, ""),
        new SearchProperties.Workspace("http://localhost", 1000, 2),
        new SearchProperties.ContentSource("http://localhost", 1000, "content-service"),
        null,
        new SearchProperties.Internal(null, null),
        new SearchProperties.Reindex(true, 100, 10, 100, false, 300, 30));
  }

  private record FakeProvider(SearchProviderType type, boolean fail) implements SearchProvider {
    @Override
    public SearchIndexResult upsert(SearchIndexDocument document) {
      if (fail) {
        throw new SearchProviderException("SEARCH_PROVIDER_UNAVAILABLE", "failed");
      }
      return new SearchIndexResult(document.noteId(), Instant.now(), false);
    }

    @Override
    public SearchArchiveResult archive(UUID noteId, Instant archivedAt) {
      if (fail) {
        throw new SearchProviderException("SEARCH_PROVIDER_UNAVAILABLE", "failed");
      }
      return new SearchArchiveResult(noteId, archivedAt, true);
    }

    @Override
    public PageResponse<SearchNoteResult> search(SearchQuery query) {
      if (fail) {
        throw new SearchProviderException("SEARCH_PROVIDER_UNAVAILABLE", "failed");
      }
      SearchNoteResult result =
          new SearchNoteResult(
              UUID.randomUUID(),
              query.workspaceId(),
              query.notebookId(),
              "Title",
              "Title",
              1.0d,
              Instant.now());
      return new PageResponse<>(List.of(result), query.page(), query.size(), 1, 1, true);
    }

    @Override
    public boolean health() {
      return !fail;
    }
  }
}
