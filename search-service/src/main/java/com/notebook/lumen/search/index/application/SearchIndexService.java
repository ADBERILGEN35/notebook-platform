package com.notebook.lumen.search.index.application;

import com.notebook.lumen.search.index.api.IndexDocumentRequest;
import com.notebook.lumen.search.index.api.IndexDocumentResponse;
import com.notebook.lumen.search.index.domain.SearchDocument;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchIndexService {
  private final SearchDocumentRepository repository;
  private final ContentBlockTextExtractor textExtractor;
  private final SearchAuditService auditService;

  public SearchIndexService(
      SearchDocumentRepository repository,
      ContentBlockTextExtractor textExtractor,
      SearchAuditService auditService) {
    this.repository = repository;
    this.textExtractor = textExtractor;
    this.auditService = auditService;
  }

  @Transactional
  public IndexDocumentResponse upsert(IndexDocumentRequest request) {
    Instant now = Instant.now();
    String contentText = textExtractor.extract(request.contentBlocks());
    String tagsText = request.tags() == null ? null : String.join(" ", request.tags());
    SearchDocument document =
        repository
            .findByNoteId(request.noteId())
            .map(existing -> updateExisting(existing, request, contentText, tagsText, now))
            .orElseGet(() -> create(request, contentText, tagsText, now));
    repository.save(document);
    auditService.record(
        "SEARCH_DOCUMENT_INDEXED",
        document.getWorkspaceId(),
        document.getId(),
        Map.of(
            "documentId",
            document.getId().toString(),
            "noteId",
            document.getNoteId().toString(),
            "sourceVersion",
            String.valueOf(document.getSourceVersion())));
    return new IndexDocumentResponse(
        document.getId(), document.getNoteId(), document.getIndexedAt());
  }

  @Transactional
  public void archive(UUID noteId) {
    repository
        .findByNoteId(noteId)
        .ifPresent(
            document -> {
              document.archive(Instant.now(), Instant.now());
              auditService.record(
                  "SEARCH_DOCUMENT_ARCHIVED",
                  document.getWorkspaceId(),
                  document.getId(),
                  Map.of("documentId", document.getId().toString(), "noteId", noteId.toString()));
            });
  }

  private SearchDocument updateExisting(
      SearchDocument existing,
      IndexDocumentRequest request,
      String contentText,
      String tagsText,
      Instant now) {
    if (existing.newerThan(request.sourceVersion())) {
      return existing;
    }
    existing.apply(
        request.workspaceId(),
        request.notebookId(),
        request.noteId(),
        request.title(),
        contentText,
        tagsText,
        request.notebookName(),
        request.createdBy(),
        request.updatedBy(),
        request.noteCreatedAt(),
        request.noteUpdatedAt(),
        request.archivedAt(),
        request.sourceVersion(),
        now);
    return existing;
  }

  private SearchDocument create(
      IndexDocumentRequest request, String contentText, String tagsText, Instant now) {
    return new SearchDocument(
        UUID.randomUUID(),
        request.workspaceId(),
        request.notebookId(),
        request.noteId(),
        request.title(),
        contentText,
        tagsText,
        request.notebookName(),
        request.createdBy(),
        request.updatedBy(),
        request.noteCreatedAt(),
        request.noteUpdatedAt(),
        request.archivedAt(),
        request.sourceVersion(),
        now);
  }
}
