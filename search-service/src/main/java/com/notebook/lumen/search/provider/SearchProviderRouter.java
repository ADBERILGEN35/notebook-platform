package com.notebook.lumen.search.provider;

import com.notebook.lumen.search.provider.postgres.PostgreSqlSearchProvider;
import com.notebook.lumen.search.query.dto.PageResponse;
import com.notebook.lumen.search.query.dto.SearchNoteResult;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SearchProviderRouter {
  private final SearchProperties properties;
  private final PostgreSqlSearchProvider postgresProvider;
  private final List<SearchProvider> providers;
  private final SearchProviderMetrics metrics;

  public SearchProviderRouter(
      SearchProperties properties,
      PostgreSqlSearchProvider postgresProvider,
      List<SearchProvider> providers,
      SearchProviderMetrics metrics) {
    this.properties = properties;
    this.postgresProvider = postgresProvider;
    this.providers = providers;
    this.metrics = metrics;
  }

  public SearchProviderType selectedType() {
    try {
      return SearchProviderType.from(properties.provider());
    } catch (IllegalArgumentException e) {
      throw new SearchException(
          HttpStatus.BAD_REQUEST, "SEARCH_PROVIDER_MISCONFIGURED", e.getMessage());
    }
  }

  public void projectUpsert(SearchIndexDocument document) {
    if (selectedType() == SearchProviderType.OPENSEARCH || properties.dualWriteEnabled()) {
      provider(SearchProviderType.OPENSEARCH).upsert(document);
    }
  }

  public void projectArchive(UUID noteId, Instant archivedAt) {
    if (selectedType() == SearchProviderType.OPENSEARCH || properties.dualWriteEnabled()) {
      provider(SearchProviderType.OPENSEARCH).archive(noteId, archivedAt);
    }
  }

  public PageResponse<SearchNoteResult> search(SearchQuery query) {
    SearchProviderType selected = selectedType();
    SearchProvider provider = provider(selected);
    if (selected == SearchProviderType.POSTGRES) {
      return metrics.record(selected, SearchOperation.SEARCH, () -> provider.search(query));
    }
    try {
      return provider.search(query);
    } catch (RuntimeException e) {
      if (!properties.fallbackToPostgres()) {
        throw e;
      }
      metrics.fallback(SearchOperation.SEARCH);
      return metrics.record(
          SearchProviderType.POSTGRES,
          SearchOperation.SEARCH,
          () -> postgresProvider.search(query));
    }
  }

  public boolean health(SearchProviderType type) {
    return provider(type).health();
  }

  private SearchProvider provider(SearchProviderType type) {
    return providers.stream()
        .filter(candidate -> candidate.type() == type)
        .findFirst()
        .orElseThrow(
            () ->
                new SearchException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEARCH_PROVIDER_UNAVAILABLE",
                    "Search provider is unavailable: " + type));
  }
}
