package com.notebook.lumen.search.index.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.search.index.api.IndexDocumentRequest;
import com.notebook.lumen.search.index.domain.SearchDocument;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SearchIndexServiceTest {
  private final SearchDocumentRepository repository = mock(SearchDocumentRepository.class);
  private final ContentBlockTextExtractor extractor = mock(ContentBlockTextExtractor.class);
  private final SearchAuditService auditService = mock(SearchAuditService.class);
  private final SearchIndexService service =
      new SearchIndexService(repository, extractor, auditService);

  @Test
  void upsertCreatesSearchDocument() throws Exception {
    when(repository.findByNoteId(any())).thenReturn(Optional.empty());
    when(extractor.extract(any())).thenReturn("body text");

    var response = service.upsert(request(1));

    assertThat(response.documentId()).isNotNull();
    verify(repository).save(any(SearchDocument.class));
  }

  @Test
  void olderSourceVersionKeepsExistingDocument() throws Exception {
    SearchDocument existing =
        new SearchDocument(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Current",
            "current body",
            "",
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            3,
            Instant.now());
    when(repository.findByNoteId(existing.getNoteId())).thenReturn(Optional.of(existing));
    when(extractor.extract(any())).thenReturn("old body");

    service.upsert(
        request(existing.getWorkspaceId(), existing.getNotebookId(), existing.getNoteId(), 2));

    assertThat(existing.getTitle()).isEqualTo("Current");
    assertThat(existing.getSourceVersion()).isEqualTo(3);
  }

  @Test
  void reindexMarksSeenDocumentAndCanRestoreArchivedDocument() throws Exception {
    SearchDocument existing =
        new SearchDocument(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Archived",
            "body",
            "",
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            1,
            Instant.now());
    UUID jobId = UUID.randomUUID();
    when(repository.findByNoteId(existing.getNoteId())).thenReturn(Optional.of(existing));
    when(extractor.extract(any())).thenReturn("new body");

    service.upsertForReindex(
        request(existing.getWorkspaceId(), existing.getNotebookId(), existing.getNoteId(), 2),
        jobId);

    assertThat(existing.getArchivedAt()).isNull();
    assertThat(existing.getLastSeenReindexJobId()).isEqualTo(jobId);
    assertThat(existing.getLastSeenReindexAt()).isNotNull();
  }

  private IndexDocumentRequest request(int sourceVersion) throws Exception {
    return request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sourceVersion);
  }

  private IndexDocumentRequest request(
      UUID workspaceId, UUID notebookId, UUID noteId, int sourceVersion) throws Exception {
    return new IndexDocumentRequest(
        workspaceId,
        notebookId,
        noteId,
        "Roadmap",
        JsonMapper.builder().build().readTree("[{\"text\":\"body\"}]"),
        List.of("tag"),
        "Notebook",
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now(),
        Instant.now(),
        null,
        sourceVersion);
  }
}
