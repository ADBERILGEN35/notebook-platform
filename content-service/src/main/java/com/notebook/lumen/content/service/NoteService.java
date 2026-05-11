package com.notebook.lumen.content.service;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.client.WorkspaceClient;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.domain.*;
import com.notebook.lumen.content.dto.*;
import com.notebook.lumen.content.dto.Requests.*;
import com.notebook.lumen.content.mapper.ContentMapper;
import com.notebook.lumen.content.repository.*;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.exception.ContentException;
import com.notebook.lumen.content.tenant.StrictWorkspaceHeaderValidator;
import com.notebook.lumen.content.tenant.TenantDatabaseSession;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
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
  private final SearchIndexingService searchIndexingService;
  private final ContentProperties contentProperties;
  private final NoteEtagSupport noteEtagSupport;
  private final Counter noteConflictDetectedCounter;
  private final Counter noteUpdateWithoutIfMatchCounter;
  private final Counter notePreconditionRequiredCounter;

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
      StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator,
      SearchIndexingService searchIndexingService,
      ContentProperties contentProperties,
      NoteEtagSupport noteEtagSupport,
      MeterRegistry meterRegistry) {
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
    this.searchIndexingService = searchIndexingService;
    this.contentProperties = contentProperties;
    this.noteEtagSupport = noteEtagSupport;
    this.noteConflictDetectedCounter =
        meterRegistry.counter("note_conflict_detected_total", "service", "content-service");
    this.noteUpdateWithoutIfMatchCounter =
        meterRegistry.counter("note_update_without_if_match_total", "service", "content-service");
    this.notePreconditionRequiredCounter =
        meterRegistry.counter("note_precondition_required_total", "service", "content-service");
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
    searchIndexingService.upsert(note, request.contentBlocks(), 1);
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
  public NoteResponse update(
      UserContext user, UUID noteId, UpdateNoteRequest request, String ifMatchHeader) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireWritable(user.userId(), note.getNotebookId());
    enforceIfMatch(note, ifMatchHeader, user, "NOTE_UPDATE_WITHOUT_IF_MATCH");
    blockValidationService.validate(request.contentBlocks());
    return applyNoteUpdate(
        note,
        user.userId(),
        request.title(),
        request.contentBlocks(),
        schema(request.contentSchemaVersion()),
        "NOTE_UPDATED",
        Map.of("notebookId", note.getNotebookId().toString()));
  }

  @Transactional
  public MergeApplyResult applyMerged(
      UserContext user,
      UUID noteId,
      String ifMatchHeader,
      String mergedTitle,
      tools.jackson.databind.JsonNode mergedBlocks,
      int mergeVersion,
      String baseEtag,
      String idempotencyKey) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireWritable(user.userId(), note.getNotebookId());
    enforceIfMatch(note, ifMatchHeader, user, "NOTE_MERGE_APPLY_WITHOUT_IF_MATCH");
    blockValidationService.validate(mergedBlocks);
    NoteResponse response =
        applyNoteUpdate(
            note,
            user.userId(),
            mergedTitle,
            mergedBlocks,
            note.getContentSchemaVersion(),
            "NOTE_MERGE_APPLIED",
            Map.of(
                "notebookId",
                note.getNotebookId().toString(),
                "mergeVersion",
                mergeVersion,
                "baseEtag",
                baseEtag == null ? "" : baseEtag,
                "expectedRemoteEtag",
                ifMatchHeader,
                "idempotencyKeyPresent",
                idempotencyKey != null && !idempotencyKey.isBlank()));
    int versionNumber = versionRepository.countByNoteId(noteId);
    return new MergeApplyResult(response, versionNumber);
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
    searchIndexingService.archive(note);
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
  public NoteResponse restore(
      UserContext user, UUID noteId, int versionNumber, String ifMatchHeader) {
    Note note = load(noteId);
    tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
    assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
    permissionService.requireWritable(user.userId(), note.getNotebookId());
    enforceIfMatch(note, ifMatchHeader, user, "NOTE_RESTORED_WITHOUT_IF_MATCH");
    NoteVersion version = loadVersion(noteId, versionNumber);
    Instant now = Instant.now();
    int newVersionNumber = nextVersion(noteId);
    note.update(
        version.getTitle(),
        version.getContentBlocks(),
        version.getContentSchemaVersion(),
        user.userId(),
        now);
    createVersion(note, newVersionNumber, user.userId(), now);
    replaceLinks(note, mapper.toResponse(version).contentBlocks(), now);
    auditService.record(
        "NOTE_RESTORED",
        user.userId(),
        note.getWorkspaceId(),
        "NOTE",
        note.getId(),
        Map.of("restoredVersion", versionNumber));
    searchIndexingService.upsert(
        note, mapper.toResponse(version).contentBlocks(), newVersionNumber);
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

  private NoteResponse applyNoteUpdate(
      Note note,
      UUID actorUserId,
      String title,
      tools.jackson.databind.JsonNode contentBlocks,
      int contentSchemaVersion,
      String auditEventType,
      Map<String, Object> auditMetadata) {
    Instant now = Instant.now();
    int versionNumber = nextVersion(note.getId());
    note.update(title, mapper.write(contentBlocks), contentSchemaVersion, actorUserId, now);
    createVersion(note, versionNumber, actorUserId, now);
    replaceLinks(note, contentBlocks, now);
    Map<String, Object> finalAuditMetadata = new LinkedHashMap<>(auditMetadata);
    if ("NOTE_MERGE_APPLIED".equals(auditEventType)) {
      finalAuditMetadata.put("resultVersion", versionNumber);
      finalAuditMetadata.put("conflictCount", 0);
      finalAuditMetadata.put("source", "backend_apply");
    }
    auditService.record(
        auditEventType,
        actorUserId,
        note.getWorkspaceId(),
        "NOTE",
        note.getId(),
        finalAuditMetadata);
    searchIndexingService.upsert(note, contentBlocks, versionNumber);
    return mapper.toResponse(note);
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

  private void enforceIfMatch(
      Note note, String ifMatchHeader, UserContext user, String missingEventType) {
    boolean requiresIfMatch =
        contentProperties.concurrency() != null
            && contentProperties.concurrency().requireIfMatchForNoteUpdate();
    if (ifMatchHeader == null || ifMatchHeader.isBlank()) {
      if (requiresIfMatch) {
        notePreconditionRequiredCounter.increment();
        throw new ContentException(
            HttpStatus.PRECONDITION_REQUIRED,
            "PRECONDITION_REQUIRED",
            "If-Match header is required for note updates");
      }
      noteUpdateWithoutIfMatchCounter.increment();
      auditService.record(
          missingEventType,
          user.userId(),
          note.getWorkspaceId(),
          "NOTE",
          note.getId(),
          Map.of("notebookId", note.getNotebookId().toString()));
      return;
    }

    long expectedRevision = noteEtagSupport.parseIfMatchRevision(ifMatchHeader);
    if (expectedRevision == note.getNoteRevision()) {
      return;
    }

    noteConflictDetectedCounter.increment();
    auditService.record(
        "NOTE_CONFLICT_DETECTED",
        user.userId(),
        note.getWorkspaceId(),
        "NOTE",
        note.getId(),
        Map.of(
            "notebookId",
            note.getNotebookId().toString(),
            "expectedRevision",
            expectedRevision,
            "actualRevision",
            note.getNoteRevision()));
    throw new ContentException(
        HttpStatus.PRECONDITION_FAILED,
        "NOTE_CONFLICT",
        "Note changed on server, reload latest version before saving");
  }

  public record MergeApplyResult(NoteResponse note, int versionNumber) {}
}
