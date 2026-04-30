package com.notebook.lumen.content.mapper;

import com.notebook.lumen.content.domain.*;
import com.notebook.lumen.content.dto.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ContentMapper {
  private final ObjectMapper objectMapper;

  public ContentMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public NoteResponse toResponse(Note n) {
    return new NoteResponse(
        n.getId(),
        n.getWorkspaceId(),
        n.getNotebookId(),
        n.getParentNoteId(),
        n.getTitle(),
        read(n.getContentBlocks()),
        n.getContentSchemaVersion(),
        n.getCreatedBy(),
        n.getUpdatedBy(),
        n.getCreatedAt(),
        n.getUpdatedAt(),
        n.getArchivedAt());
  }

  public NoteVersionResponse toResponse(NoteVersion v) {
    return new NoteVersionResponse(
        v.getId(),
        v.getWorkspaceId(),
        v.getNoteId(),
        v.getVersionNumber(),
        v.getTitle(),
        read(v.getContentBlocks()),
        v.getContentSchemaVersion(),
        v.getCreatedBy(),
        v.getCreatedAt());
  }

  public NoteLinkResponse toResponse(NoteLink l) {
    return new NoteLinkResponse(
        l.getFromNoteId(), l.getToNoteId(), l.getWorkspaceId(), l.getCreatedAt());
  }

  public CommentResponse toResponse(Comment c) {
    return new CommentResponse(
        c.getId(),
        c.getWorkspaceId(),
        c.getNoteId(),
        c.getUserId(),
        c.getParentCommentId(),
        c.getBlockId(),
        c.getContent(),
        c.getResolvedAt(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getDeletedAt());
  }

  public NoteTagResponse toResponse(NoteTag t) {
    return new NoteTagResponse(t.getNoteId(), t.getTagId(), t.getWorkspaceId(), t.getCreatedAt());
  }

  public String write(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  private JsonNode read(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("Stored JSON is invalid", e);
    }
  }
}
