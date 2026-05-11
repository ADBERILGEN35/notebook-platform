package com.notebook.lumen.search.index.application;

import com.notebook.lumen.search.index.api.IndexDocumentRequest;
import com.notebook.lumen.search.index.api.IndexDocumentResponse;
import com.notebook.lumen.search.index.domain.SearchDocument;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentRepository;
import com.notebook.lumen.search.provider.SearchIndexDocument;
import com.notebook.lumen.search.provider.SearchProviderRouter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchIndexService {
  private final SearchDocumentRepository repository;
  private final ContentBlockTextExtractor textExtractor;
  private final SearchAuditService auditService;
  private final SearchProviderRouter providerRouter;
  private final SearchPermissionSnapshotService permissionSnapshotService;
  private final MeterRegistry meterRegistry;

  public SearchIndexService(
      SearchDocumentRepository repository,
      ContentBlockTextExtractor textExtractor,
      SearchAuditService auditService,
      SearchProviderRouter providerRouter,
      SearchPermissionSnapshotService permissionSnapshotService,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.textExtractor = textExtractor;
    this.auditService = auditService;
    this.providerRouter = providerRouter;
    this.permissionSnapshotService = permissionSnapshotService;
    this.meterRegistry = meterRegistry;
  }

  public SearchIndexService(
      SearchDocumentRepository repository,
      ContentBlockTextExtractor textExtractor,
      SearchAuditService auditService,
      SearchProviderRouter providerRouter) {
    this(repository, textExtractor, auditService, providerRouter, null, new SimpleMeterRegistry());
  }

  @Transactional
  public IndexDocumentResponse upsert(IndexDocumentRequest request) {
    return upsert(request, null);
  }

  @Transactional
  public IndexDocumentResponse upsertForReindex(IndexDocumentRequest request, UUID reindexJobId) {
    return upsert(request, reindexJobId);
  }

  private IndexDocumentResponse upsert(IndexDocumentRequest request, UUID reindexJobId) {
    Instant now = Instant.now();
    String contentText = textExtractor.extract(request.contentBlocks());
    String tagsText = request.tags() == null ? null : String.join(" ", request.tags());
    SearchPermissionSnapshotService.PermissionSnapshot permissionSnapshot =
        permissionSnapshotService == null
            ? new SearchPermissionSnapshotService.PermissionSnapshot(
                request.workspaceId(),
                request.notebookId(),
                "WORKSPACE",
                true,
                false,
                null,
                now,
                false)
            : permissionSnapshotService.resolve(request.workspaceId(), request.notebookId());
    boolean[] skipped = new boolean[] {false};
    SearchDocument document =
        repository
            .findByNoteId(request.noteId())
            .map(
                existing ->
                    updateExisting(
                        existing,
                        request,
                        contentText,
                        tagsText,
                        permissionSnapshot,
                        now,
                        reindexJobId,
                        skipped))
            .orElseGet(() -> create(request, contentText, tagsText, permissionSnapshot, now));
    if (reindexJobId != null) {
      document.markSeenForReindex(reindexJobId, now);
    }
    repository.save(document);
    if (!skipped[0]) {
      providerRouter.projectUpsert(toProviderDocument(document));
    }
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
              providerRouter.projectArchive(noteId, document.getArchivedAt());
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
      SearchPermissionSnapshotService.PermissionSnapshot permissionSnapshot,
      Instant now,
      UUID reindexJobId,
      boolean[] skipped) {
    if (existing.newerThan(request.sourceVersion())) {
      if (reindexJobId != null) {
        existing.markSeenForReindex(reindexJobId, now);
      }
      skipped[0] = true;
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
        permissionSnapshot.permissionVersion(),
        permissionSnapshot.visibilityMode(),
        permissionSnapshot.workspaceReadable(),
        permissionSnapshot.restricted(),
        permissionSnapshot.permissionIndexedAt(),
        now);
    return existing;
  }

  private SearchDocument create(
      IndexDocumentRequest request,
      String contentText,
      String tagsText,
      SearchPermissionSnapshotService.PermissionSnapshot permissionSnapshot,
      Instant now) {
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
        permissionSnapshot.permissionVersion(),
        permissionSnapshot.visibilityMode(),
        permissionSnapshot.workspaceReadable(),
        permissionSnapshot.restricted(),
        permissionSnapshot.permissionIndexedAt(),
        now);
  }

  private SearchIndexDocument toProviderDocument(SearchDocument document) {
    return new SearchIndexDocument(
        document.getId(),
        document.getWorkspaceId(),
        document.getNotebookId(),
        document.getNoteId(),
        document.getTitle(),
        document.getContentText(),
        document.getTagsText(),
        document.getNotebookName(),
        document.getCreatedBy(),
        document.getUpdatedBy(),
        document.getNoteCreatedAt(),
        document.getNoteUpdatedAt(),
        document.getArchivedAt(),
        document.getSourceVersion(),
        document.getPermissionVersion(),
        document.getVisibilityMode(),
        document.isWorkspaceReadable(),
        document.isRestricted(),
        document.getPermissionIndexedAt(),
        document.getIndexedAt());
  }

  @Transactional
  public void refreshNotebookPermissionSnapshot(UUID notebookId) {
    List<SearchDocument> documents = repository.findByNotebookId(notebookId);
    if (documents.isEmpty()) {
      meterRegistry.counter("search_permission_refresh_total", "status", "success").increment();
      return;
    }

    SearchDocument anchor = documents.getFirst();
    SearchPermissionSnapshotService.PermissionSnapshot snapshot =
        permissionSnapshotService.resolve(anchor.getWorkspaceId(), notebookId);
    if (!snapshot.fromSource()) {
      meterRegistry.counter("search_permission_refresh_total", "status", "failed").increment();
      auditService.record(
          "SEARCH_PERMISSION_REFRESH_FAILED",
          anchor.getWorkspaceId(),
          notebookId,
          Map.of("reason", "snapshot-unavailable"));
      return;
    }

    Instant now = Instant.now();
    for (SearchDocument document : documents) {
      document.apply(
          document.getWorkspaceId(),
          document.getNotebookId(),
          document.getNoteId(),
          document.getTitle(),
          document.getContentText(),
          document.getTagsText(),
          document.getNotebookName(),
          document.getCreatedBy(),
          document.getUpdatedBy(),
          document.getNoteCreatedAt(),
          document.getNoteUpdatedAt(),
          document.getArchivedAt(),
          document.getSourceVersion(),
          snapshot.permissionVersion(),
          snapshot.visibilityMode(),
          snapshot.workspaceReadable(),
          snapshot.restricted(),
          snapshot.permissionIndexedAt() == null ? now : snapshot.permissionIndexedAt(),
          now);
      providerRouter.projectUpsert(toProviderDocument(document));
    }

    meterRegistry.counter("search_permission_refresh_total", "status", "success").increment();
    auditService.record(
        "SEARCH_PERMISSION_REFRESH_COMPLETED",
        anchor.getWorkspaceId(),
        notebookId,
        Map.of("updatedDocuments", String.valueOf(documents.size())));
  }
}
