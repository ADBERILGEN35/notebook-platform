package com.notebook.lumen.content.search.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.repository.NoteTagRepository;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEvent;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEventType;
import com.notebook.lumen.content.search.outbox.infrastructure.SearchIndexOutboxRepository;
import com.notebook.lumen.content.service.SearchIndexingService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SearchIndexingServiceTest {
  private final SearchIndexOutboxRepository repository =
      org.mockito.Mockito.mock(SearchIndexOutboxRepository.class);
  private final NoteTagRepository tagRepository = org.mockito.Mockito.mock(NoteTagRepository.class);
  private final AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
  private final SearchIndexingService service =
      new SearchIndexingService(
          repository, tagRepository, properties(true), auditService, objectMapper);

  @Test
  void upsertQueuesOutboxEventWithStableIdempotencyKey() {
    Note note = note();
    when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(tagRepository.findByIdNoteId(note.getId())).thenReturn(List.of());

    service.upsert(note, objectMapper.createObjectNode().put("text", "secret body"), 3);

    ArgumentCaptor<SearchIndexOutboxEvent> eventCaptor =
        ArgumentCaptor.forClass(SearchIndexOutboxEvent.class);
    verify(repository).save(eventCaptor.capture());
    SearchIndexOutboxEvent event = eventCaptor.getValue();

    assertThat(event.getEventType()).isEqualTo(SearchIndexOutboxEventType.NOTE_UPSERT);
    assertThat(event.getIdempotencyKey())
        .isEqualTo("search-index:" + note.getId() + ":3:NOTE_UPSERT");
    assertThat(event.getPayload()).contains("\"sourceVersion\":3");
    verify(auditService)
        .record(
            org.mockito.ArgumentMatchers.eq("SEARCH_INDEX_OUTBOX_QUEUED"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(note.getWorkspaceId()),
            org.mockito.ArgumentMatchers.eq("NOTE"),
            org.mockito.ArgumentMatchers.eq(note.getId()),
            org.mockito.ArgumentMatchers.argThat(
                metadata -> !metadata.toString().contains("secret body")));
  }

  @Test
  void duplicateIdempotencyKeyDoesNotCreateAnotherEvent() {
    Note note = note();
    when(repository.findByIdempotencyKey("search-index:" + note.getId() + ":3:NOTE_UPSERT"))
        .thenReturn(Optional.of(existingEvent(note)));

    service.upsert(note, objectMapper.createObjectNode(), 3);

    verify(repository, never()).save(any());
    verify(auditService, never())
        .record(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyMap());
  }

  @Test
  void archiveQueuesArchiveEventWithoutContentPayload() {
    Note note = note();
    note.archive(Instant.parse("2026-05-06T10:15:30Z"), UUID.randomUUID());
    when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

    service.archive(note);

    ArgumentCaptor<SearchIndexOutboxEvent> eventCaptor =
        ArgumentCaptor.forClass(SearchIndexOutboxEvent.class);
    verify(repository).save(eventCaptor.capture());

    SearchIndexOutboxEvent event = eventCaptor.getValue();
    assertThat(event.getEventType()).isEqualTo(SearchIndexOutboxEventType.NOTE_ARCHIVE);
    assertThat(event.getPayload()).contains(note.getId().toString());
    assertThat(event.getPayload()).doesNotContain("secret body");
  }

  private Note note() {
    UUID userId = UUID.randomUUID();
    return new Note(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "Phase 26",
        "{}",
        1,
        userId,
        Instant.parse("2026-05-06T10:00:00Z"));
  }

  private SearchIndexOutboxEvent existingEvent(Note note) {
    return new SearchIndexOutboxEvent(
        UUID.randomUUID(),
        SearchIndexOutboxEventType.NOTE_UPSERT,
        note.getWorkspaceId(),
        note.getNotebookId(),
        note.getId(),
        3,
        "{}",
        "search-index:" + note.getId() + ":3:NOTE_UPSERT",
        Instant.now());
  }

  private ContentProperties properties(boolean enabled) {
    return new ContentProperties(
        "",
        null,
        null,
        null,
        new ContentProperties.Search("", 1000, enabled, null, null, outbox()));
  }

  private ContentProperties.SearchOutbox outbox() {
    return new ContentProperties.SearchOutbox(true, 50, 10, 30, 3600, 10, 300, null);
  }
}
