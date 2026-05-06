package com.notebook.lumen.content.search.source.application;

import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.repository.NoteRepository;
import com.notebook.lumen.content.repository.NoteTagRepository;
import com.notebook.lumen.content.repository.NoteVersionRepository;
import com.notebook.lumen.content.search.source.api.SearchIndexSourceNoteResponse;
import com.notebook.lumen.content.search.source.api.SearchIndexSourcePageResponse;
import com.notebook.lumen.content.shared.exception.ContentException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SearchIndexSourceService {
  private static final int DEFAULT_SIZE = 100;
  private static final int MAX_SIZE = 500;

  private final NoteRepository noteRepository;
  private final NoteTagRepository noteTagRepository;
  private final NoteVersionRepository noteVersionRepository;
  private final ObjectMapper objectMapper;

  public SearchIndexSourceService(
      NoteRepository noteRepository,
      NoteTagRepository noteTagRepository,
      NoteVersionRepository noteVersionRepository,
      ObjectMapper objectMapper) {
    this.noteRepository = noteRepository;
    this.noteTagRepository = noteTagRepository;
    this.noteVersionRepository = noteVersionRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public SearchIndexSourcePageResponse notes(
      UUID workspaceId, UUID notebookId, String cursor, int size) {
    int effectiveSize = effectiveSize(size);
    SearchIndexSourceCursor decoded = SearchIndexSourceCursor.decode(cursor);
    Page<Note> page =
        noteRepository.findSearchIndexSource(
            workspaceId,
            notebookId,
            decoded == null ? null : decoded.updatedAt(),
            decoded == null ? null : decoded.noteId(),
            PageRequest.of(0, effectiveSize));
    Map<UUID, List<String>> tagsByNote = tagsByNote(page.getContent());
    List<SearchIndexSourceNoteResponse> items =
        page.getContent().stream().map(note -> toResponse(note, tagsByNote)).toList();
    String nextCursor = nextCursor(page.getContent(), page.hasNext());
    return new SearchIndexSourcePageResponse(items, nextCursor, page.hasNext());
  }

  private int effectiveSize(int size) {
    int requested = size <= 0 ? DEFAULT_SIZE : size;
    if (requested > MAX_SIZE) {
      throw new ContentException(
          HttpStatus.BAD_REQUEST,
          "INVALID_SEARCH_INDEX_SOURCE_REQUEST",
          "size must be <= " + MAX_SIZE);
    }
    return requested;
  }

  private Map<UUID, List<String>> tagsByNote(List<Note> notes) {
    if (notes.isEmpty()) {
      return Map.of();
    }
    return noteTagRepository.findByIdNoteIdIn(notes.stream().map(Note::getId).toList()).stream()
        .collect(
            Collectors.groupingBy(
                tag -> tag.getId().getNoteId(),
                Collectors.mapping(tag -> tag.getTagId().toString(), Collectors.toList())));
  }

  private SearchIndexSourceNoteResponse toResponse(Note note, Map<UUID, List<String>> tagsByNote) {
    try {
      return new SearchIndexSourceNoteResponse(
          note.getWorkspaceId(),
          note.getNotebookId(),
          note.getId(),
          note.getTitle(),
          objectMapper.readTree(note.getContentBlocks()),
          tagsByNote.getOrDefault(note.getId(), List.of()),
          null,
          note.getCreatedBy(),
          note.getUpdatedBy(),
          note.getCreatedAt(),
          note.getUpdatedAt(),
          note.getArchivedAt(),
          noteVersionRepository.countByNoteId(note.getId()));
    } catch (RuntimeException e) {
      throw new ContentException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "INVALID_SEARCH_INDEX_SOURCE_REQUEST",
          "Stored note content is invalid");
    }
  }

  private String nextCursor(List<Note> notes, boolean hasNext) {
    if (!hasNext || notes.isEmpty()) {
      return null;
    }
    Note last = notes.getLast();
    return SearchIndexSourceCursor.encode(last.getUpdatedAt(), last.getId());
  }
}
