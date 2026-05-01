package com.notebook.lumen.workspace.service;

import com.notebook.lumen.workspace.audit.AuditService;
import com.notebook.lumen.workspace.domain.*;
import com.notebook.lumen.workspace.dto.*;
import com.notebook.lumen.workspace.dto.Requests.*;
import com.notebook.lumen.workspace.mapper.WorkspaceMapper;
import com.notebook.lumen.workspace.repository.*;
import com.notebook.lumen.workspace.shared.UserContext;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import com.notebook.lumen.workspace.tenant.StrictWorkspaceHeaderValidator;
import com.notebook.lumen.workspace.tenant.TenantDatabaseSession;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotebookService {
  public static final Set<String> NOTEBOOK_SORTS = Set.of("name", "createdAt", "updatedAt");
  public static final Set<String> MEMBER_SORTS = Set.of("createdAt", "updatedAt", "role");

  private final NotebookRepository notebookRepository;
  private final NotebookMemberRepository notebookMemberRepository;
  private final AuthorizationService authorizationService;
  private final WorkspaceMapper mapper;
  private final AuditService auditService;
  private final TenantDatabaseSession tenantDatabaseSession;
  private final StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator;

  public NotebookService(
      NotebookRepository notebookRepository,
      NotebookMemberRepository notebookMemberRepository,
      AuthorizationService authorizationService,
      WorkspaceMapper mapper,
      AuditService auditService,
      TenantDatabaseSession tenantDatabaseSession,
      StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator) {
    this.notebookRepository = notebookRepository;
    this.notebookMemberRepository = notebookMemberRepository;
    this.authorizationService = authorizationService;
    this.mapper = mapper;
    this.auditService = auditService;
    this.tenantDatabaseSession = tenantDatabaseSession;
    this.strictWorkspaceHeaderValidator = strictWorkspaceHeaderValidator;
  }

  @Transactional
  public NotebookResponse create(
      UserContext user, UUID workspaceId, CreateNotebookRequest request) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceRole(
        workspaceId, user.userId(), WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    Instant now = Instant.now();
    Notebook notebook =
        new Notebook(
            UUID.randomUUID(), workspaceId, request.name(), request.icon(), user.userId(), now);
    notebookRepository.save(notebook);
    notebookMemberRepository.save(
        new NotebookMember(notebook.getId(), workspaceId, user.userId(), NotebookRole.OWNER, now));
    auditService.record(
        "NOTEBOOK_CREATED", user.userId(), workspaceId, "NOTEBOOK", notebook.getId(), Map.of());
    return mapper.toResponse(notebook);
  }

  @Transactional(readOnly = true)
  public PageResponse<NotebookResponse> list(
      UserContext user, UUID workspaceId, Pageable pageable) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceMember(workspaceId, user.userId());
    return PageResponse.from(
        notebookRepository
            .findByWorkspaceIdAndArchivedAtIsNull(workspaceId, pageable)
            .map(mapper::toResponse));
  }

  @Transactional(readOnly = true)
  public NotebookResponse get(UserContext user, UUID notebookId) {
    Notebook notebook = load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    authorizationService.requireNotebookRead(notebook, user.userId());
    return mapper.toResponse(notebook);
  }

  @Transactional
  public NotebookResponse update(UserContext user, UUID notebookId, UpdateNotebookRequest request) {
    Notebook notebook = load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    authorizationService.requireNotebookManage(notebook, user.userId());
    notebook.update(request.name(), request.icon(), Instant.now());
    return mapper.toResponse(notebook);
  }

  @Transactional
  public void archive(UserContext user, UUID notebookId) {
    Notebook notebook = load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    authorizationService.requireNotebookFullControl(notebook, user.userId());
    notebook.archive(Instant.now());
  }

  @Transactional(readOnly = true)
  public PageResponse<NotebookMemberResponse> members(
      UserContext user, UUID notebookId, Pageable pageable) {
    Notebook notebook = load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    authorizationService.requireNotebookRead(notebook, user.userId());
    return PageResponse.from(
        notebookMemberRepository.findByIdNotebookId(notebookId, pageable).map(mapper::toResponse));
  }

  @Transactional
  public NotebookMemberResponse upsertMember(
      UserContext user, UUID notebookId, UUID targetUserId, UpsertNotebookMemberRequest request) {
    Notebook notebook = load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    authorizationService.requireNotebookFullControl(notebook, user.userId());
    authorizationService.requireWorkspaceMember(notebook.getWorkspaceId(), targetUserId);
    NotebookMember member =
        notebookMemberRepository
            .findByIdNotebookIdAndIdUserId(notebookId, targetUserId)
            .orElseGet(
                () ->
                    new NotebookMember(
                        notebookId,
                        notebook.getWorkspaceId(),
                        targetUserId,
                        request.role(),
                        Instant.now()));
    member.changeRole(request.role(), Instant.now());
    NotebookMember saved = notebookMemberRepository.save(member);
    auditService.record(
        "NOTEBOOK_MEMBER_CHANGED",
        user.userId(),
        notebook.getWorkspaceId(),
        "NOTEBOOK_MEMBER",
        targetUserId,
        Map.of("notebookId", notebookId.toString(), "role", request.role().name()));
    return mapper.toResponse(saved);
  }

  @Transactional
  public NotebookMemberResponse updateMemberRole(
      UserContext user,
      UUID notebookId,
      UUID targetUserId,
      UpdateNotebookMemberRoleRequest request) {
    Notebook notebook = load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    authorizationService.requireNotebookFullControl(notebook, user.userId());
    NotebookMember member =
        notebookMemberRepository
            .findByIdNotebookIdAndIdUserId(notebookId, targetUserId)
            .orElseThrow(
                () ->
                    Exceptions.notFound("NOTEBOOK_MEMBER_NOT_FOUND", "Notebook member not found"));
    member.changeRole(request.role(), Instant.now());
    auditService.record(
        "NOTEBOOK_MEMBER_CHANGED",
        user.userId(),
        notebook.getWorkspaceId(),
        "NOTEBOOK_MEMBER",
        targetUserId,
        Map.of("notebookId", notebookId.toString(), "role", request.role().name()));
    return mapper.toResponse(member);
  }

  @Transactional
  public void removeMember(UserContext user, UUID notebookId, UUID targetUserId) {
    Notebook notebook = load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    authorizationService.requireNotebookFullControl(notebook, user.userId());
    NotebookMember member =
        notebookMemberRepository
            .findByIdNotebookIdAndIdUserId(notebookId, targetUserId)
            .orElseThrow(
                () ->
                    Exceptions.notFound("NOTEBOOK_MEMBER_NOT_FOUND", "Notebook member not found"));
    notebookMemberRepository.delete(member);
  }

  Notebook load(UUID notebookId) {
    return notebookRepository
        .findByIdAndArchivedAtIsNull(notebookId)
        .orElseThrow(() -> Exceptions.notFound("NOTEBOOK_NOT_FOUND", "Notebook not found"));
  }
}
