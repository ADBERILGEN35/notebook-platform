package com.notebook.lumen.search.provider.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.search.provider.SearchIndexDocument;
import com.notebook.lumen.search.provider.SearchQuery;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenSearchQueryBuilderTest {
  private final OpenSearchQueryBuilder builder = new OpenSearchQueryBuilder();

  @Test
  void searchBodyAlwaysIncludesWorkspaceAndArchivedFilters() {
    UUID workspaceId = UUID.randomUUID();

    String body = builder.searchBody(new SearchQuery(workspaceId, null, "roadmap", 0, 20, true));

    assertThat(body).contains("\"workspaceId\":\"" + workspaceId + "\"");
    assertThat(body).contains("\"must_not\":{\"exists\":{\"field\":\"archivedAt\"}}");
  }

  @Test
  void searchBodyIncludesNotebookFilterWhenProvided() {
    UUID notebookId = UUID.randomUUID();

    String body =
        builder.searchBody(new SearchQuery(UUID.randomUUID(), notebookId, "roadmap", 0, 20, true));

    assertThat(body).contains("\"notebookId\":\"" + notebookId + "\"");
  }

  @Test
  void searchBodyUsesExpectedBoosts() {
    String body =
        builder.searchBody(new SearchQuery(UUID.randomUUID(), null, "roadmap", 0, 20, true));

    assertThat(body)
        .contains("\"title^3\"")
        .contains("\"tagsText^2\"")
        .contains("\"notebookName^2\"")
        .contains("\"contentText\"");
  }

  @Test
  void archiveBodySetsArchivedAtWithoutDelete() {
    Instant archivedAt = Instant.parse("2026-05-07T10:15:30Z");

    String body = builder.archiveBody(archivedAt);

    assertThat(body).contains("\"archivedAt\":\"2026-05-07T10:15:30Z\"");
    assertThat(body).doesNotContain("delete");
  }

  @Test
  void upsertBodyUsesNoteIdAsDocumentSource() {
    UUID noteId = UUID.randomUUID();

    String body =
        builder.upsertBody(
            new SearchIndexDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                noteId,
                "Title",
                "Body",
                "tag",
                "Notebook",
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now(),
                null,
                3,
                Instant.now()));

    assertThat(body).contains("\"noteId\":\"" + noteId + "\"");
    assertThat(body).contains("\"sourceVersion\":3");
  }
}
