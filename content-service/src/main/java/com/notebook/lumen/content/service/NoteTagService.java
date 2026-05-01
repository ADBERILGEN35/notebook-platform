package com.notebook.lumen.content.service;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.domain.NoteTag;
import com.notebook.lumen.content.domain.NoteTagId;
import com.notebook.lumen.content.dto.NoteTagResponse;
import com.notebook.lumen.content.dto.PageResponse;
import com.notebook.lumen.content.mapper.ContentMapper;
import com.notebook.lumen.content.repository.NoteTagRepository;
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
public class NoteTagService {
  public static final Set<String> NOTE_TAG_SORTS = Set.of("createdAt");
  private final NoteTagRepository noteTagRepository;
  private final NoteService noteService;
  private final PermissionService permissionService;
  private final ContentMapper mapper;
  private final AuditService auditService;
  private final TenantDatabaseSession tenantDatabaseSession;

  public NoteTagService(
      NoteTagRepository noteTagRepository,
      NoteService noteService,
      PermissionService permissionService,
      ContentMapper mapper,
      AuditService auditService,
      TenantDatabaseSession tenantDatabaseSession) {
    this.noteTagRepository = noteTagRepository;
    this.noteService = noteService;
    this.permissionService = permissionService;
    this.mapper = mapper;
    this.auditService = auditService;
    this.tenantDatabaseSession = tenantDatabaseSession;
  }

  @Transactional
  public NoteTagResponse attach(UserContext user, UUID noteId, UUID tagId) {
    Note note = noteService.load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireWritable(user.userId(), note.getNotebookId());
    permissionService.requireTag(user.userId(), note.getWorkspaceId(), tagId);
    NoteTagId id = new NoteTagId(noteId, tagId);
    if (noteTagRepository.existsById(id))
      throw new ContentException(
          HttpStatus.CONFLICT, "DUPLICATE_NOTE_TAG", "Note tag already exists");
    NoteTag saved =
        noteTagRepository.save(new NoteTag(noteId, tagId, note.getWorkspaceId(), Instant.now()));
    auditService.record(
        "NOTE_TAG_ATTACHED",
        user.userId(),
        note.getWorkspaceId(),
        "NOTE_TAG",
        tagId,
        Map.of("noteId", noteId.toString()));
    return mapper.toResponse(saved);
  }

  @Transactional
  public void detach(UserContext user, UUID noteId, UUID tagId) {
    Note note = noteService.load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireWritable(user.userId(), note.getNotebookId());
    noteTagRepository.deleteById(new NoteTagId(noteId, tagId));
    auditService.record(
        "NOTE_TAG_DETACHED",
        user.userId(),
        note.getWorkspaceId(),
        "NOTE_TAG",
        tagId,
        Map.of("noteId", noteId.toString()));
  }

  @Transactional(readOnly = true)
  public PageResponse<NoteTagResponse> list(UserContext user, UUID noteId, Pageable pageable) {
    Note note = noteService.load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireReadable(user.userId(), note.getNotebookId());
    return PageResponse.from(
        noteTagRepository.findByIdNoteId(noteId, pageable).map(mapper::toResponse));
  }
}
