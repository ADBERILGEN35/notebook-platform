package com.notebook.lumen.content.search.source.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.repository.NoteRepository;
import com.notebook.lumen.content.repository.NoteTagRepository;
import com.notebook.lumen.content.repository.NoteVersionRepository;
import com.notebook.lumen.content.search.source.api.SearchIndexSourcePageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

class SearchIndexSourceServiceTest {
  private final NoteRepository noteRepository = org.mockito.Mockito.mock(NoteRepository.class);
  private final NoteTagRepository tagRepository = org.mockito.Mockito.mock(NoteTagRepository.class);
  private final NoteVersionRepository versionRepository =
      org.mockito.Mockito.mock(NoteVersionRepository.class);
  private final SearchIndexSourceService service =
      new SearchIndexSourceService(
          noteRepository, tagRepository, versionRepository, JsonMapper.builder().build());

  @Test
  void returnsArchivedNotesWithCursorAndNoUnauthorizedFields() {
    Note note = note();
    note.archive(Instant.parse("2026-05-06T11:00:00Z"), UUID.randomUUID());
    when(noteRepository.findSearchIndexSource(
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(note), Pageable.ofSize(1), 2));
    when(tagRepository.findByIdNoteIdIn(List.of(note.getId()))).thenReturn(List.of());
    when(versionRepository.countByNoteId(note.getId())).thenReturn(7);

    SearchIndexSourcePageResponse response = service.notes(null, null, null, 1);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().archivedAt()).isNotNull();
    assertThat(response.items().getFirst().sourceVersion()).isEqualTo(7);
    assertThat(response.nextCursor()).isNotBlank();
    assertThat(response.toString()).doesNotContain("password");
  }

  private Note note() {
    return new Note(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "Reindex",
        "[{\"type\":\"paragraph\",\"content\":\"body\"}]",
        1,
        UUID.randomUUID(),
        Instant.parse("2026-05-06T10:00:00Z"));
  }
}
