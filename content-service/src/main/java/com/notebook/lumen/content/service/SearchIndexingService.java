package com.notebook.lumen.content.service;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.client.search.SearchClient;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.repository.NoteTagRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class SearchIndexingService {
  private static final Logger log = LoggerFactory.getLogger(SearchIndexingService.class);

  private final SearchClient searchClient;
  private final NoteTagRepository noteTagRepository;
  private final ContentProperties properties;
  private final AuditService auditService;

  public SearchIndexingService(
      SearchClient searchClient,
      NoteTagRepository noteTagRepository,
      ContentProperties properties,
      AuditService auditService) {
    this.searchClient = searchClient;
    this.noteTagRepository = noteTagRepository;
    this.properties = properties;
    this.auditService = auditService;
  }

  public void upsert(Note note, JsonNode contentBlocks, int sourceVersion) {
    if (!enabled()) {
      return;
    }
    try {
      searchClient.upsert(
          new SearchClient.IndexDocumentRequest(
              note.getWorkspaceId(),
              note.getNotebookId(),
              note.getId(),
              note.getTitle(),
              contentBlocks,
              tags(note.getId()),
              null,
              note.getCreatedBy(),
              note.getUpdatedBy(),
              note.getCreatedAt(),
              note.getUpdatedAt(),
              note.getArchivedAt(),
              sourceVersion));
    } catch (RuntimeException e) {
      recordFailure(note.getWorkspaceId(), note.getId(), "upsert", e);
    }
  }

  public void archive(Note note) {
    if (!enabled()) {
      return;
    }
    try {
      searchClient.archive(note.getId());
    } catch (RuntimeException e) {
      recordFailure(note.getWorkspaceId(), note.getId(), "archive", e);
    }
  }

  private boolean enabled() {
    return properties.search() != null && properties.search().enabled();
  }

  private java.util.List<String> tags(UUID noteId) {
    return noteTagRepository.findByIdNoteId(noteId).stream()
        .map(tag -> tag.getTagId().toString())
        .toList();
  }

  private void recordFailure(UUID workspaceId, UUID noteId, String operation, RuntimeException e) {
    log.warn(
        "Search indexing failed workspaceId={} noteId={} operation={} errorClass={}",
        workspaceId,
        noteId,
        operation,
        e.getClass().getSimpleName());
    auditService.record(
        "SEARCH_INDEXING_FAILED",
        null,
        workspaceId,
        "NOTE",
        noteId,
        Map.of("operation", operation, "error", e.getClass().getSimpleName()));
  }
}
