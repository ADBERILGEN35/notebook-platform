package com.notebook.lumen.content.service;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.domain.Comment;
import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.dto.CommentResponse;
import com.notebook.lumen.content.dto.PageResponse;
import com.notebook.lumen.content.dto.Requests.*;
import com.notebook.lumen.content.mapper.ContentMapper;
import com.notebook.lumen.content.repository.CommentRepository;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.exception.ContentException;
import com.notebook.lumen.content.tenant.TenantDatabaseSession;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
  public static final Set<String> COMMENT_SORTS = Set.of("createdAt", "updatedAt");
  private final CommentRepository commentRepository;
  private final NoteService noteService;
  private final PermissionService permissionService;
  private final ContentMapper mapper;
  private final AuditService auditService;
  private final TenantDatabaseSession tenantDatabaseSession;

  public CommentService(
      CommentRepository commentRepository,
      NoteService noteService,
      PermissionService permissionService,
      ContentMapper mapper,
      AuditService auditService,
      TenantDatabaseSession tenantDatabaseSession) {
    this.commentRepository = commentRepository;
    this.noteService = noteService;
    this.permissionService = permissionService;
    this.mapper = mapper;
    this.auditService = auditService;
    this.tenantDatabaseSession = tenantDatabaseSession;
  }

  @Transactional
  public CommentResponse create(UserContext user, UUID noteId, CreateCommentRequest request) {
    Note note = noteService.load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireCommentable(user.userId(), note.getNotebookId());
    if (request.parentCommentId() != null) {
      Comment parent = load(request.parentCommentId());
      if (!parent.getNoteId().equals(noteId))
        throw bad("INVALID_PARENT_COMMENT", "Parent comment must belong to same note");
    }
    Comment comment =
        new Comment(
            UUID.randomUUID(),
            note.getWorkspaceId(),
            noteId,
            user.userId(),
            request.parentCommentId(),
            request.blockId(),
            request.content(),
            Instant.now());
    Comment saved = commentRepository.save(comment);
    auditService.record(
        "COMMENT_CREATED",
        user.userId(),
        note.getWorkspaceId(),
        "COMMENT",
        saved.getId(),
        Map.of("noteId", noteId.toString()));
    return mapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<CommentResponse> list(UserContext user, UUID noteId, Pageable pageable) {
    Note note = noteService.load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireReadable(user.userId(), note.getNotebookId());
    return PageResponse.from(
        commentRepository.findByNoteIdAndDeletedAtIsNull(noteId, pageable).map(mapper::toResponse));
  }

  @Transactional
  public CommentResponse update(UserContext user, UUID commentId, UpdateCommentRequest request) {
    Comment comment = load(commentId);
    Note note = noteService.load(comment.getNoteId());
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    if (!comment.getUserId().equals(user.userId())
        && !permissionService.canManage(user.userId(), note.getNotebookId()))
      throw new ContentException(
          HttpStatus.FORBIDDEN, "COMMENT_ACCESS_DENIED", "Cannot update comment");
    comment.update(request.content(), Instant.now());
    return mapper.toResponse(comment);
  }

  @Transactional
  public void delete(UserContext user, UUID commentId) {
    Comment comment = load(commentId);
    Note note = noteService.load(comment.getNoteId());
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    if (!comment.getUserId().equals(user.userId())
        && !permissionService.canManage(user.userId(), note.getNotebookId()))
      throw new ContentException(
          HttpStatus.FORBIDDEN, "COMMENT_ACCESS_DENIED", "Cannot delete comment");
    comment.delete(Instant.now());
  }

  @Transactional
  public CommentResponse resolve(UserContext user, UUID commentId) {
    return setResolved(user, commentId, true);
  }

  @Transactional
  public CommentResponse reopen(UserContext user, UUID commentId) {
    return setResolved(user, commentId, false);
  }

  private CommentResponse setResolved(UserContext user, UUID commentId, boolean resolved) {
    Comment comment = load(commentId);
    Note note = noteService.load(comment.getNoteId());
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    if (!comment.getUserId().equals(user.userId())
        && !permissionService.canManage(user.userId(), note.getNotebookId()))
      permissionService.requireWritable(user.userId(), note.getNotebookId());
    if (resolved) comment.resolve(Instant.now());
    else comment.reopen(Instant.now());
    if (resolved) {
      auditService.record(
          "COMMENT_RESOLVED",
          user.userId(),
          note.getWorkspaceId(),
          "COMMENT",
          comment.getId(),
          Map.of("noteId", note.getId().toString()));
    }
    return mapper.toResponse(comment);
  }

  private Comment load(UUID id) {
    return commentRepository
        .findByIdAndDeletedAtIsNull(id)
        .orElseThrow(
            () ->
                new ContentException(
                    HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "Comment not found"));
  }

  private ContentException bad(String code, String message) {
    return new ContentException(HttpStatus.BAD_REQUEST, code, message);
  }
}
