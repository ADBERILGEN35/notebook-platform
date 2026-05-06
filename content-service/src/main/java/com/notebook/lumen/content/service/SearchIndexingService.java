package com.notebook.lumen.content.service;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.client.search.SearchClient;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.repository.NoteTagRepository;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEvent;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEventType;
import com.notebook.lumen.content.search.outbox.infrastructure.SearchIndexOutboxRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SearchIndexingService {
  private static final Logger log = LoggerFactory.getLogger(SearchIndexingService.class);

  private final SearchIndexOutboxRepository outboxRepository;
  private final NoteTagRepository noteTagRepository;
  private final ContentProperties properties;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public SearchIndexingService(
      SearchIndexOutboxRepository outboxRepository,
      NoteTagRepository noteTagRepository,
      ContentProperties properties,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.noteTagRepository = noteTagRepository;
    this.properties = properties;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void upsert(Note note, JsonNode contentBlocks, int sourceVersion) {
    if (!enabled()) {
      return;
    }
    SearchClient.IndexDocumentRequest request =
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
            sourceVersion);
    enqueue(
        SearchIndexOutboxEventType.NOTE_UPSERT,
        note,
        sourceVersion,
        idempotencyKey(
            note.getId(), String.valueOf(sourceVersion), SearchIndexOutboxEventType.NOTE_UPSERT),
        serialize(request));
  }

  @Transactional
  public void archive(Note note) {
    if (!enabled()) {
      return;
    }
    String archiveVersion =
        note.getUpdatedAt() == null
            ? String.valueOf(System.currentTimeMillis())
            : String.valueOf(note.getUpdatedAt().toEpochMilli());
    enqueue(
        SearchIndexOutboxEventType.NOTE_ARCHIVE,
        note,
        null,
        idempotencyKey(note.getId(), archiveVersion, SearchIndexOutboxEventType.NOTE_ARCHIVE),
        serialize(Map.of("noteId", note.getId().toString())));
  }

  private boolean enabled() {
    return properties.search() != null && properties.search().enabled();
  }

  private java.util.List<String> tags(UUID noteId) {
    return noteTagRepository.findByIdNoteId(noteId).stream()
        .map(tag -> tag.getTagId().toString())
        .toList();
  }

  private void enqueue(
      SearchIndexOutboxEventType eventType,
      Note note,
      Integer sourceVersion,
      String idempotencyKey,
      String payload) {
    if (outboxRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
      return;
    }
    Instant now = Instant.now();
    outboxRepository.save(
        new SearchIndexOutboxEvent(
            UUID.randomUUID(),
            eventType,
            note.getWorkspaceId(),
            note.getNotebookId(),
            note.getId(),
            sourceVersion,
            payload,
            idempotencyKey,
            now));
    log.debug(
        "Queued search index outbox event workspaceId={} noteId={} eventType={} sourceVersion={}",
        note.getWorkspaceId(),
        note.getId(),
        eventType,
        sourceVersion);
    auditService.record(
        "SEARCH_INDEX_OUTBOX_QUEUED",
        null,
        note.getWorkspaceId(),
        "NOTE",
        note.getId(),
        Map.of(
            "eventType",
            eventType.name(),
            "sourceVersion",
            sourceVersion == null ? "" : sourceVersion.toString()));
  }

  private String idempotencyKey(UUID noteId, String version, SearchIndexOutboxEventType eventType) {
    return "search-index:" + noteId + ":" + version + ":" + eventType.name();
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid search index outbox payload", e);
    }
  }
}
