package com.notebook.lumen.content.service;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.client.WorkspaceClient;
import com.notebook.lumen.content.domain.*;
import com.notebook.lumen.content.dto.*;
import com.notebook.lumen.content.dto.Requests.*;
import com.notebook.lumen.content.mapper.ContentMapper;
import com.notebook.lumen.content.repository.*;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.exception.ContentException;
import com.notebook.lumen.content.tenant.StrictWorkspaceHeaderValidator;
import com.notebook.lumen.content.tenant.TenantDatabaseSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
  public static final Set<String> NOTE_SORTS = Set.of("title", "createdAt", "updatedAt");
  public static final Set<String> VERSION_SORTS = Set.of("versionNumber", "createdAt");
  public static final Set<String> LINK_SORTS = Set.of("createdAt");
  public static final Set<String> SEARCH_SORTS = Set.of("updatedAt", "createdAt");
  private final NoteRepository noteRepository;
  private final NoteVersionRepository versionRepository;
  private final NoteLinkRepository linkRepository;
  private final BlockValidationService blockValidationService;
  private final NoteLinkParser linkParser;
  private final PermissionService permissionService;
  private final ContentMapper mapper;
  private final AuditService auditService;
  private final TenantDatabaseSession tenantDatabaseSession;
  private final StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator;

  public NoteService(
      NoteRepository noteRepository,
      NoteVersionRepository versionRepository,
      NoteLinkRepository linkRepository,
      BlockValidationService blockValidationService,
      NoteLinkParser linkParser,
      PermissionService permissionService,
      ContentMapper mapper,
      AuditService auditService,
      TenantDatabaseSession tenantDatabaseSession,
      StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator) {
    this.noteRepository = noteRepository;
    this.versionRepository = versionRepository;
    this.linkRepository = linkRepository;
    this.blockValidationService = blockValidationService;
    this.linkParser = linkParser;
    this.permissionService = permissionService;
    this.mapper = mapper;
    this.auditService = auditService;
    this.tenantDatabaseSession = tenantDatabaseSession;
    this.strictWorkspaceHeaderValidator = strictWorkspaceHeaderValidator;
  }

  @Transactional
  public NoteResponse create(UserContext user, UUID notebookId, CreateNoteRequest request) {
    WorkspaceClient.NotebookPermissionResponse p =
        permissionService.requireWritable(user.userId(), notebookId);
    tenantDatabaseSession.applyWorkspace(p.workspaceId());
    assertAggregateWorkspaceHeader(user, p.workspaceId());
    if (request.parentNoteId() != null
        && !noteRepository.existsByIdAndWorkspaceIdAndArchivedAtIsNull(
            request.parentNoteId(), p.workspaceId()))
      throw bad("PARENT_NOTE_NOT_FOUND", "Parent note not found in workspace");
    blockValidationService.validate(request.contentBlocks());
    Instant now = Instant.now();
    int schema = schema(request.contentSchemaVersion());
    Note note =
        new Note(
            UUID.randomUUID(),
            p.workspaceId(),
            notebookId,
            request.parentNoteId(),
            request.title(),
            mapper.write(request.contentBlocks()),
            schema,
            user.userId(),
            now);
    noteRepository.save(note);
    createVersion(note, 1, user.userId(), now);
    replaceLinks(note, request.contentBlocks(), now);
    auditService.record(
        "NOTE_CREATED",
        user.userId(),
        note.getWorkspaceId(),
        "NOTE",
        note.getId(),
        Map.of("notebookId", notebookId.toString()));
    return mapper.toResponse(note);
  }

  @Transactional(readOnly = true)
  public NoteResponse get(UserContext user, UUID noteId) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireReadable(user.userId(), note.getNotebookId());
    return mapper.toResponse(note);
  }

  @Transactional(readOnly = true)
  public PageResponse<NoteResponse> list(UserContext user, UUID notebookId, Pageable pageable) {
    WorkspaceClient.NotebookPermissionResponse p =
        permissionService.requireReadable(user.userId(), notebookId);
    tenantDatabaseSession.applyWorkspace(p.workspaceId());
    assertAggregateWorkspaceHeader(user, p.workspaceId());
    return PageResponse.from(
        noteRepository
            .findByNotebookIdAndArchivedAtIsNull(notebookId, pageable)
            .map(mapper::toResponse));
  }

  @Transactional
  public NoteResponse update(UserContext user, UUID noteId, UpdateNoteRequest request) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireWritable(user.userId(), note.getNotebookId());
    blockValidationService.validate(request.contentBlocks());
    note.update(
        request.title(),
        mapper.write(request.contentBlocks()),
        schema(request.contentSchemaVersion()),
        user.userId(),
        Instant.now());
    createVersion(note, nextVersion(note.getId()), user.userId(), Instant.now());
    replaceLinks(note, request.contentBlocks(), Instant.now());
    auditService.record(
        "NOTE_UPDATED",
        user.userId(),
        note.getWorkspaceId(),
        "NOTE",
        note.getId(),
        Map.of("notebookId", note.getNotebookId().toString()));
    return mapper.toResponse(note);
  }

  @Transactional
  public void archive(UserContext user, UUID noteId) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireWritable(user.userId(), note.getNotebookId());
    note.archive(Instant.now(), user.userId());
    auditService.record(
        "NOTE_ARCHIVED", user.userId(), note.getWorkspaceId(), "NOTE", note.getId(), Map.of());
  }

  @Transactional(readOnly = true)
  public PageResponse<NoteVersionResponse> versions(
      UserContext user, UUID noteId, Pageable pageable) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireReadable(user.userId(), note.getNotebookId());
    return PageResponse.from(
        versionRepository.findByNoteId(noteId, pageable).map(mapper::toResponse));
  }

  @Transactional(readOnly = true)
  public NoteVersionResponse version(UserContext user, UUID noteId, int versionNumber) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireReadable(user.userId(), note.getNotebookId());
    return mapper.toResponse(loadVersion(noteId, versionNumber));
  }

  @Transactional
  public NoteResponse restore(UserContext user, UUID noteId, int versionNumber) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireWritable(user.userId(), note.getNotebookId());
    NoteVersion version = loadVersion(noteId, versionNumber);
    Instant now = Instant.now();
    note.update(
        version.getTitle(),
        version.getContentBlocks(),
        version.getContentSchemaVersion(),
        user.userId(),
        now);
    createVersion(note, nextVersion(noteId), user.userId(), now);
    replaceLinks(note, mapper.toResponse(version).contentBlocks(), now);
    auditService.record(
        "NOTE_RESTORED",
        user.userId(),
        note.getWorkspaceId(),
        "NOTE",
        note.getId(),
        Map.of("restoredVersion", versionNumber));
    return mapper.toResponse(note);
  }

  @Transactional(readOnly = true)
  public PageResponse<NoteLinkResponse> outgoing(UserContext user, UUID noteId, Pageable pageable) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireReadable(user.userId(), note.getNotebookId());
    return PageResponse.from(
        linkRepository.findByIdFromNoteId(noteId, pageable).map(mapper::toResponse));
  }

  @Transactional(readOnly = true)
  public PageResponse<NoteLinkResponse> incoming(UserContext user, UUID noteId, Pageable pageable) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireReadable(user.userId(), note.getNotebookId());
    return PageResponse.from(
        linkRepository.findByIdToNoteId(noteId, pageable).map(mapper::toResponse));
  }

  @Transactional(readOnly = true)
  public PageResponse<NoteResponse> search(
      UserContext user, UUID workspaceId, String q, Pageable pageable) {
    if (q == null || q.length() > 120) throw bad("VALIDATION_ERROR", "q length must be <= 120");
    tenantDatabaseSession.applyWorkspace(workspaceId);
    assertWorkspaceHeaderIfPresent(user, workspaceId);
    Page<Note> page = noteRepository.search(workspaceId, q.trim(), pageable);
    List<NoteResponse> permitted =
        page.getContent().stream()
            .filter(
                n -> {
                  try {
                    permissionService.requireReadable(user.userId(), n.getNotebookId());
                    return true;
                  } catch (ContentException e) {
                    return false;
                  }
                })
            .map(mapper::toResponse)
            .toList();
    return PageResponse.from(new PageImpl<>(permitted, pageable, permitted.size()));
  }

  Note load(UUID noteId) {
    return noteRepository
        .findByIdAndArchivedAtIsNull(noteId)
        .orElseThrow(
            () -> new ContentException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "Note not found"));
  }

  void assertAggregateWorkspaceHeader(UserContext user, UUID workspaceId) {
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, workspaceId);
  }

  void assertWorkspaceHeaderIfPresent(UserContext user, UUID workspaceId) {
    strictWorkspaceHeaderValidator.validateIfPresent(user, workspaceId);
  }

  private NoteVersion loadVersion(UUID noteId, int versionNumber) {
    return versionRepository
        .findByNoteIdAndVersionNumber(noteId, versionNumber)
        .orElseThrow(
            () ->
                new ContentException(
                    HttpStatus.NOT_FOUND, "NOTE_VERSION_NOT_FOUND", "Note version not found"));
  }

  private int schema(Integer schema) {
    return schema == null ? 1 : schema;
  }

  private int nextVersion(UUID noteId) {
    return versionRepository.countByNoteId(noteId) + 1;
  }

  private void createVersion(Note note, int versionNumber, UUID userId, Instant now) {
    versionRepository.save(
        new NoteVersion(
            UUID.randomUUID(),
            note.getWorkspaceId(),
            note.getId(),
            versionNumber,
            note.getTitle(),
            note.getContentBlocks(),
            note.getContentSchemaVersion(),
            userId,
            now));
  }

  private void replaceLinks(Note note, tools.jackson.databind.JsonNode blocks, Instant now) {
    Set<UUID> targets = linkParser.parse(blocks, note.getId());
    for (UUID target : targets)
      if (!noteRepository.existsByIdAndWorkspaceIdAndArchivedAtIsNull(
          target, note.getWorkspaceId()))
        throw bad("INVALID_NOTE_LINK", "Linked note must exist in same workspace");
    linkRepository.deleteByIdFromNoteId(note.getId());
    targets.forEach(
        target ->
            linkRepository.save(new NoteLink(note.getId(), target, note.getWorkspaceId(), now)));
  }

  private ContentException bad(String code, String message) {
    return new ContentException(HttpStatus.BAD_REQUEST, code, message);
  }
}
