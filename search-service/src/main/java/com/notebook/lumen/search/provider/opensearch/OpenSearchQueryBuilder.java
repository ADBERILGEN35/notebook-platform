package com.notebook.lumen.search.provider.opensearch;

import com.notebook.lumen.search.provider.SearchIndexDocument;
import com.notebook.lumen.search.provider.SearchQuery;
import java.time.Instant;

public class OpenSearchQueryBuilder {
  public String searchBody(SearchQuery query) {
    String notebookFilter =
        query.notebookId() == null
            ? ""
            : """
            ,{"term":{"notebookId":"%s"}}
            """
                .formatted(query.notebookId());
    return """
        {"from":%d,"size":%d,"query":{"bool":{"filter":[{"term":{"workspaceId":"%s"}},{"bool":{"must_not":{"exists":{"field":"archivedAt"}}}}%s],"must":[{"multi_match":{"query":"%s","fields":["title^3","tagsText^2","notebookName^2","contentText"],"type":"best_fields"}}]}},"sort":[{"_score":{"order":"desc"}},{"noteUpdatedAt":{"order":"desc","missing":"_last"}}]}
        """
        .formatted(
            Math.max(0, query.providerPage()) * query.providerSize(),
            query.providerSize(),
            query.workspaceId(),
            notebookFilter,
            json(query.q()));
  }

  public String upsertBody(SearchIndexDocument document) {
    return """
        {"noteId":"%s","workspaceId":"%s","notebookId":%s,"title":"%s","contentText":"%s","tagsText":"%s","notebookName":"%s","createdBy":%s,"updatedBy":%s,"noteCreatedAt":%s,"noteUpdatedAt":%s,"archivedAt":%s,"sourceVersion":%s,"permissionVersion":%s,"visibilityMode":%s,"workspaceReadable":%s,"restricted":%s,"permissionIndexedAt":%s,"indexedAt":"%s"}
        """
        .formatted(
            document.noteId(),
            document.workspaceId(),
            nullableUuid(document.notebookId()),
            json(document.title()),
            json(document.contentText()),
            json(document.tagsText()),
            json(document.notebookName()),
            nullableUuid(document.createdBy()),
            nullableUuid(document.updatedBy()),
            nullableInstant(document.noteCreatedAt()),
            nullableInstant(document.noteUpdatedAt()),
            nullableInstant(document.archivedAt()),
            document.sourceVersion() == null ? "null" : document.sourceVersion().toString(),
            document.permissionVersion() == null ? "null" : document.permissionVersion().toString(),
            nullableString(document.visibilityMode()),
            String.valueOf(document.workspaceReadable()),
            String.valueOf(document.restricted()),
            nullableInstant(document.permissionIndexedAt()),
            document.indexedAt());
  }

  public String archiveBody(Instant archivedAt) {
    return """
        {"doc":{"archivedAt":"%s","indexedAt":"%s"},"doc_as_upsert":false}
        """
        .formatted(archivedAt, Instant.now());
  }

  private String nullableUuid(Object value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private String nullableInstant(Instant value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private String nullableString(String value) {
    return value == null ? "null" : "\"" + json(value) + "\"";
  }

  private String json(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }
}
