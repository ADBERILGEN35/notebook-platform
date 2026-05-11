package com.notebook.lumen.search.provider.postgres;

import com.notebook.lumen.search.index.domain.SearchDocument;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentRepository;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentSearchRow;
import com.notebook.lumen.search.provider.SearchArchiveResult;
import com.notebook.lumen.search.provider.SearchIndexDocument;
import com.notebook.lumen.search.provider.SearchIndexResult;
import com.notebook.lumen.search.provider.SearchProvider;
import com.notebook.lumen.search.provider.SearchProviderType;
import com.notebook.lumen.search.provider.SearchQuery;
import com.notebook.lumen.search.query.dto.PageResponse;
import com.notebook.lumen.search.query.dto.SearchNoteResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlSearchProvider implements SearchProvider {
  private final SearchDocumentRepository repository;

  public PostgreSqlSearchProvider(SearchDocumentRepository repository) {
    this.repository = repository;
  }

  @Override
  public SearchProviderType type() {
    return SearchProviderType.POSTGRES;
  }

  @Override
  public SearchIndexResult upsert(SearchIndexDocument document) {
    SearchDocument entity =
        repository
            .findByNoteId(document.noteId())
            .map(existing -> applyIfCurrent(existing, document))
            .orElseGet(() -> create(document));
    repository.save(entity);
    return new SearchIndexResult(entity.getNoteId(), entity.getIndexedAt(), false);
  }

  @Override
  public SearchArchiveResult archive(UUID noteId, Instant archivedAt) {
    Instant now = Instant.now();
    return repository
        .findByNoteId(noteId)
        .map(
            document -> {
              document.archive(archivedAt, now);
              repository.save(document);
              return new SearchArchiveResult(noteId, document.getArchivedAt(), true);
            })
        .orElseGet(
            () -> new SearchArchiveResult(noteId, archivedAt == null ? now : archivedAt, false));
  }

  @Override
  public PageResponse<SearchNoteResult> search(SearchQuery query) {
    var pageable = PageRequest.of(query.providerPage(), query.providerSize());
    var page = repository.search(query.workspaceId(), query.notebookId(), query.q(), pageable);
    var results = page.getContent().stream().map(row -> toResult(row, query.q())).toList();
    return PageResponse.from(new PageImpl<>(results, pageable, page.getTotalElements()));
  }

  @Override
  public boolean health() {
    try {
      repository.count();
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private SearchDocument applyIfCurrent(SearchDocument existing, SearchIndexDocument document) {
    if (existing.newerThan(document.sourceVersion())) {
      return existing;
    }
    existing.apply(
        document.workspaceId(),
        document.notebookId(),
        document.noteId(),
        document.title(),
        document.contentText(),
        document.tagsText(),
        document.notebookName(),
        document.createdBy(),
        document.updatedBy(),
        document.noteCreatedAt(),
        document.noteUpdatedAt(),
        document.archivedAt(),
        document.sourceVersion(),
        document.permissionVersion(),
        document.visibilityMode(),
        document.workspaceReadable(),
        document.restricted(),
        document.permissionIndexedAt(),
        document.indexedAt());
    return existing;
  }

  private SearchDocument create(SearchIndexDocument document) {
    return new SearchDocument(
        document.documentId() == null ? UUID.randomUUID() : document.documentId(),
        document.workspaceId(),
        document.notebookId(),
        document.noteId(),
        document.title(),
        document.contentText(),
        document.tagsText(),
        document.notebookName(),
        document.createdBy(),
        document.updatedBy(),
        document.noteCreatedAt(),
        document.noteUpdatedAt(),
        document.archivedAt(),
        document.sourceVersion(),
        document.permissionVersion(),
        document.visibilityMode(),
        document.workspaceReadable(),
        document.restricted(),
        document.permissionIndexedAt(),
        document.indexedAt());
  }

  private SearchNoteResult toResult(SearchDocumentSearchRow document, String query) {
    return new SearchNoteResult(
        document.getNoteId(),
        document.getWorkspaceId(),
        document.getNotebookId(),
        document.getTitle(),
        snippet(document.getTitle(), query),
        document.getRank() == null ? 0.0d : document.getRank(),
        document.getNoteUpdatedAt(),
        document.getVisibilityMode(),
        document.getPermissionVersion(),
        document.getWorkspaceReadable(),
        document.getRestricted());
  }

  private String snippet(String title, String query) {
    if (title == null || title.isBlank()) {
      return "";
    }
    return title.length() <= 180 ? title : title.substring(0, 180);
  }
}
