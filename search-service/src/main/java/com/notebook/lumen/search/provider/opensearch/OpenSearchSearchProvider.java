package com.notebook.lumen.search.provider.opensearch;

import com.notebook.lumen.search.provider.SearchArchiveResult;
import com.notebook.lumen.search.provider.SearchIndexDocument;
import com.notebook.lumen.search.provider.SearchIndexResult;
import com.notebook.lumen.search.provider.SearchOperation;
import com.notebook.lumen.search.provider.SearchProvider;
import com.notebook.lumen.search.provider.SearchProviderException;
import com.notebook.lumen.search.provider.SearchProviderMetrics;
import com.notebook.lumen.search.provider.SearchProviderType;
import com.notebook.lumen.search.provider.SearchQuery;
import com.notebook.lumen.search.query.dto.PageResponse;
import com.notebook.lumen.search.query.dto.SearchNoteResult;
import com.notebook.lumen.search.shared.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OpenSearchSearchProvider implements SearchProvider {
  private final SearchProperties properties;
  private final OpenSearchClient client;
  private final OpenSearchQueryBuilder queryBuilder;
  private final SearchProviderMetrics providerMetrics;
  private final MeterRegistry meterRegistry;
  private final ObjectMapper objectMapper;

  public OpenSearchSearchProvider(
      SearchProperties properties,
      OpenSearchClient client,
      SearchProviderMetrics providerMetrics,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.client = client;
    this.queryBuilder = new OpenSearchQueryBuilder();
    this.providerMetrics = providerMetrics;
    this.meterRegistry = meterRegistry;
    this.objectMapper = JsonMapper.builder().findAndAddModules().build();
  }

  @Override
  public SearchProviderType type() {
    return SearchProviderType.OPENSEARCH;
  }

  @Override
  public SearchIndexResult upsert(SearchIndexDocument document) {
    return providerMetrics.record(
        type(),
        SearchOperation.UPSERT,
        () -> {
          client.put(
              "/" + index() + "/_doc/" + document.noteId(),
              queryBuilder.upsertBody(document));
          meterRegistry.counter("opensearch_index_upserts_total").increment();
          return new SearchIndexResult(document.noteId(), document.indexedAt(), false);
        });
  }

  @Override
  public SearchArchiveResult archive(UUID noteId, Instant archivedAt) {
    Instant effectiveArchivedAt = archivedAt == null ? Instant.now() : archivedAt;
    return providerMetrics.record(
        type(),
        SearchOperation.ARCHIVE,
        () -> {
          client.post(
              "/" + index() + "/_update/" + noteId,
              queryBuilder.archiveBody(effectiveArchivedAt));
          return new SearchArchiveResult(noteId, effectiveArchivedAt, true);
        });
  }

  @Override
  public PageResponse<SearchNoteResult> search(SearchQuery query) {
    return providerMetrics.record(
        type(),
        SearchOperation.SEARCH,
        () -> {
          String response = client.post("/" + index() + "/_search", queryBuilder.searchBody(query));
          meterRegistry.counter("opensearch_search_queries_total").increment();
          return parseSearchResponse(response, query);
        });
  }

  @Override
  public boolean health() {
    try {
      client.get("/_cluster/health");
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private PageResponse<SearchNoteResult> parseSearchResponse(String response, SearchQuery query) {
    try {
      JsonNode root = objectMapper.readTree(response);
      JsonNode hits = root.get("hits");
      JsonNode hitItems = hits == null ? null : hits.get("hits");
      List<SearchNoteResult> results = new ArrayList<>();
      if (hitItems != null && hitItems.isArray()) {
        for (JsonNode hit : hitItems) {
          SearchNoteResult result = toResult(hit);
          if (result != null) {
            results.add(result);
          }
        }
      }
      long total = results.size();
      JsonNode totalNode = hits == null ? null : hits.get("total");
      if (totalNode != null && totalNode.get("value") != null) {
        total = totalNode.get("value").asLong(total);
      }
      int totalPages = query.size() <= 0 ? 1 : (int) Math.ceil((double) total / query.size());
      return new PageResponse<>(
          results,
          query.providerPage(),
          query.size(),
          total,
          totalPages,
          query.providerPage() + 1 >= totalPages);
    } catch (RuntimeException e) {
      throw new SearchProviderException(
          "OPENSEARCH_UNAVAILABLE", "OpenSearch response was not readable", e);
    }
  }

  private SearchNoteResult toResult(JsonNode hit) {
    JsonNode source = hit.get("_source");
    if (source == null || source.get("noteId") == null || source.get("workspaceId") == null) {
      return null;
    }
    return new SearchNoteResult(
        UUID.fromString(source.get("noteId").asText()),
        UUID.fromString(source.get("workspaceId").asText()),
        uuidOrNull(source.get("notebookId")),
        text(source.get("title")),
        snippet(text(source.get("title"))),
        hit.get("_score") == null ? 0.0d : hit.get("_score").asDouble(0.0d),
        instantOrNull(source.get("noteUpdatedAt")));
  }

  private UUID uuidOrNull(JsonNode node) {
    if (node == null || node.isNull() || node.asText().isBlank()) {
      return null;
    }
    return UUID.fromString(node.asText());
  }

  private Instant instantOrNull(JsonNode node) {
    if (node == null || node.isNull() || node.asText().isBlank()) {
      return null;
    }
    return Instant.parse(node.asText());
  }

  private String text(JsonNode node) {
    return node == null || node.isNull() ? "" : node.asText();
  }

  private String snippet(String title) {
    if (title == null || title.isBlank()) {
      return "";
    }
    return title.length() <= 180 ? title : title.substring(0, 180);
  }

  private String index() {
    return properties.opensearch().effectiveIndexNotes();
  }
}
