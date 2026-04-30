package com.notebook.lumen.workspace.service;

import com.notebook.lumen.workspace.domain.*;
import com.notebook.lumen.workspace.dto.Requests.*;
import com.notebook.lumen.workspace.dto.TagResponse;
import com.notebook.lumen.workspace.mapper.WorkspaceMapper;
import com.notebook.lumen.workspace.repository.*;
import com.notebook.lumen.workspace.shared.UserContext;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import com.notebook.lumen.workspace.tenant.StrictWorkspaceHeaderValidator;
import com.notebook.lumen.workspace.tenant.TenantDatabaseSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
  private final TagRepository tagRepository;
  private final NotebookTagRepository notebookTagRepository;
  private final NotebookService notebookService;
  private final AuthorizationService authorizationService;
  private final WorkspaceMapper mapper;
  private final TenantDatabaseSession tenantDatabaseSession;
  private final StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator;

  public TagService(
      TagRepository tagRepository,
      NotebookTagRepository notebookTagRepository,
      NotebookService notebookService,
      AuthorizationService authorizationService,
      WorkspaceMapper mapper,
      TenantDatabaseSession tenantDatabaseSession,
      StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator) {
    this.tagRepository = tagRepository;
    this.notebookTagRepository = notebookTagRepository;
    this.notebookService = notebookService;
    this.authorizationService = authorizationService;
    this.mapper = mapper;
    this.tenantDatabaseSession = tenantDatabaseSession;
    this.strictWorkspaceHeaderValidator = strictWorkspaceHeaderValidator;
  }

  @Transactional
  public TagResponse create(UserContext user, UUID workspaceId, CreateTagRequest request) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceRole(
        workspaceId, user.userId(), WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    if (tagRepository.existsActiveByWorkspaceNameAndScope(
        workspaceId, request.name(), request.scope())) {
      throw Exceptions.conflict("DUPLICATE_TAG", "Tag already exists in this workspace and scope");
    }
    Tag tag =
        new Tag(
            UUID.randomUUID(),
            workspaceId,
            request.name(),
            request.color(),
            request.scope(),
            Instant.now());
    return mapper.toResponse(tagRepository.save(tag));
  }

  @Transactional(readOnly = true)
  public List<TagResponse> list(UserContext user, UUID workspaceId) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceMember(workspaceId, user.userId());
    return tagRepository.findByWorkspaceIdAndArchivedAtIsNull(workspaceId).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional
  public TagResponse update(UserContext user, UUID tagId, UpdateTagRequest request) {
    Tag tag = load(tagId);
    tenantDatabaseSession.applyWorkspace(tag.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, tag.getWorkspaceId());
    authorizationService.requireWorkspaceRole(
        tag.getWorkspaceId(), user.userId(), WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    if (!tag.getName().equalsIgnoreCase(request.name())
        && tagRepository.existsActiveByWorkspaceNameAndScope(
            tag.getWorkspaceId(), request.name(), tag.getScope())) {
      throw Exceptions.conflict("DUPLICATE_TAG", "Tag already exists in this workspace and scope");
    }
    tag.update(request.name(), request.color(), Instant.now());
    return mapper.toResponse(tag);
  }

  @Transactional
  public void archive(UserContext user, UUID tagId) {
    Tag tag = load(tagId);
    tenantDatabaseSession.applyWorkspace(tag.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, tag.getWorkspaceId());
    authorizationService.requireWorkspaceRole(
        tag.getWorkspaceId(), user.userId(), WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    tag.archive(Instant.now());
  }

  @Transactional
  public void attach(UserContext user, UUID notebookId, UUID tagId) {
    Notebook notebook = notebookService.load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    Tag tag = load(tagId);
    if (!notebook.getWorkspaceId().equals(tag.getWorkspaceId())) {
      throw Exceptions.badRequest(
          "TAG_WORKSPACE_MISMATCH", "Tag and notebook must belong to the same workspace");
    }
    authorizationService.requireNotebookManage(notebook, user.userId());
    notebookTagRepository.save(
        new NotebookTag(notebookId, tagId, notebook.getWorkspaceId(), Instant.now()));
  }

  @Transactional
  public void detach(UserContext user, UUID notebookId, UUID tagId) {
    Notebook notebook = notebookService.load(notebookId);
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, notebook.getWorkspaceId());
    authorizationService.requireNotebookManage(notebook, user.userId());
    notebookTagRepository.deleteById(new NotebookTagId(notebookId, tagId));
  }

  private Tag load(UUID tagId) {
    return tagRepository
        .findByIdAndArchivedAtIsNull(tagId)
        .orElseThrow(() -> Exceptions.notFound("TAG_NOT_FOUND", "Tag not found"));
  }
}
