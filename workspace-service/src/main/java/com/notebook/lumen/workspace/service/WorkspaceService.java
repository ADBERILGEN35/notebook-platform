package com.notebook.lumen.workspace.service;

import com.notebook.lumen.workspace.audit.AuditService;
import com.notebook.lumen.workspace.domain.*;
import com.notebook.lumen.workspace.dto.Requests.*;
import com.notebook.lumen.workspace.dto.WorkspaceMemberResponse;
import com.notebook.lumen.workspace.dto.WorkspaceResponse;
import com.notebook.lumen.workspace.mapper.WorkspaceMapper;
import com.notebook.lumen.workspace.repository.*;
import com.notebook.lumen.workspace.shared.UserContext;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import com.notebook.lumen.workspace.tenant.TenantDatabaseSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final SlugService slugService;
  private final AuthorizationService authorizationService;
  private final WorkspaceMapper mapper;
  private final AuditService auditService;
  private final TenantDatabaseSession tenantDatabaseSession;

  public WorkspaceService(
      WorkspaceRepository workspaceRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      SlugService slugService,
      AuthorizationService authorizationService,
      WorkspaceMapper mapper,
      AuditService auditService,
      TenantDatabaseSession tenantDatabaseSession) {
    this.workspaceRepository = workspaceRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.slugService = slugService;
    this.authorizationService = authorizationService;
    this.mapper = mapper;
    this.auditService = auditService;
    this.tenantDatabaseSession = tenantDatabaseSession;
  }

  @Transactional
  public WorkspaceResponse create(UserContext user, CreateWorkspaceRequest request) {
    if (request.type() == WorkspaceType.PERSONAL
        && workspaceRepository.existsByOwnerIdAndTypeAndArchivedAtIsNull(
            user.userId(), WorkspaceType.PERSONAL)) {
      throw Exceptions.conflict(
          "PERSONAL_WORKSPACE_ALREADY_EXISTS", "User already has an active personal workspace");
    }

    String slug =
        request.slug() == null || request.slug().isBlank()
            ? slugService.generateUnique(request.name())
            : request.slug();
    if (request.slug() != null
        && !request.slug().isBlank()
        && workspaceRepository.existsBySlug(slug)) {
      throw Exceptions.conflict("DUPLICATE_WORKSPACE_SLUG", "Workspace slug already exists");
    }

    Instant now = Instant.now();
    UUID workspaceId = UUID.randomUUID();
    tenantDatabaseSession.applyWorkspace(workspaceId);
    Workspace workspace =
        new Workspace(workspaceId, slug, request.name(), request.type(), user.userId(), now, now);
    workspaceRepository.save(workspace);
    workspaceMemberRepository.save(
        new WorkspaceMember(workspaceId, user.userId(), WorkspaceRole.OWNER, now));
    auditService.record(
        "WORKSPACE_CREATED",
        user.userId(),
        workspaceId,
        "WORKSPACE",
        workspaceId,
        Map.of("type", workspace.getType().name()));
    return mapper.toResponse(workspace);
  }

  @Transactional(readOnly = true)
  public List<WorkspaceResponse> list(UserContext user) {
    return workspaceMemberRepository.findByIdUserId(user.userId()).stream()
        .map(
            member ->
                workspaceRepository
                    .findByIdAndArchivedAtIsNull(member.getWorkspaceId())
                    .orElse(null))
        .filter(java.util.Objects::nonNull)
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public WorkspaceResponse get(UserContext user, UUID workspaceId) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceMember(workspaceId, user.userId());
    return mapper.toResponse(loadWorkspace(workspaceId));
  }

  @Transactional
  public WorkspaceResponse update(
      UserContext user, UUID workspaceId, UpdateWorkspaceRequest request) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    WorkspaceMember member =
        authorizationService.requireWorkspaceRole(
            workspaceId, user.userId(), WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    Workspace workspace = loadWorkspace(workspaceId);
    String slug =
        request.slug() == null || request.slug().isBlank() ? workspace.getSlug() : request.slug();
    if (!workspace.getSlug().equals(slug) && workspaceRepository.existsBySlug(slug)) {
      throw Exceptions.conflict("DUPLICATE_WORKSPACE_SLUG", "Workspace slug already exists");
    }
    if (member.getRole() == WorkspaceRole.ADMIN && workspace.getType() == WorkspaceType.PERSONAL) {
      throw Exceptions.forbidden(
          "WORKSPACE_ACCESS_DENIED", "Admin cannot update personal workspace");
    }
    workspace.update(request.name(), slug, Instant.now());
    return mapper.toResponse(workspace);
  }

  @Transactional
  public void archive(UserContext user, UUID workspaceId) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceRole(workspaceId, user.userId(), WorkspaceRole.OWNER);
    loadWorkspace(workspaceId).archive(Instant.now());
    auditService.record(
        "WORKSPACE_ARCHIVED", user.userId(), workspaceId, "WORKSPACE", workspaceId, Map.of());
  }

  @Transactional(readOnly = true)
  public List<WorkspaceMemberResponse> members(UserContext user, UUID workspaceId) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceRole(
        workspaceId, user.userId(), WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.MEMBER);
    return workspaceMemberRepository.findByIdWorkspaceId(workspaceId).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional
  public WorkspaceMemberResponse updateMemberRole(
      UserContext user,
      UUID workspaceId,
      UUID targetUserId,
      UpdateWorkspaceMemberRoleRequest request) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceRole(workspaceId, user.userId(), WorkspaceRole.OWNER);
    WorkspaceMember target =
        workspaceMemberRepository
            .findByIdWorkspaceIdAndIdUserId(workspaceId, targetUserId)
            .orElseThrow(
                () ->
                    Exceptions.notFound(
                        "WORKSPACE_MEMBER_NOT_FOUND", "Workspace member not found"));
    if (target.getRole() == WorkspaceRole.OWNER
        && request.role() != WorkspaceRole.OWNER
        && ownerCount(workspaceId) <= 1) {
      throw Exceptions.conflict(
          "LAST_OWNER_CANNOT_BE_CHANGED", "Last owner role cannot be downgraded");
    }
    target.changeRole(request.role(), Instant.now());
    auditService.record(
        "WORKSPACE_MEMBER_ROLE_CHANGED",
        user.userId(),
        workspaceId,
        "WORKSPACE_MEMBER",
        targetUserId,
        Map.of("role", request.role().name()));
    return mapper.toResponse(target);
  }

  @Transactional
  public void removeMember(UserContext user, UUID workspaceId, UUID targetUserId) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceRole(workspaceId, user.userId(), WorkspaceRole.OWNER);
    WorkspaceMember target =
        workspaceMemberRepository
            .findByIdWorkspaceIdAndIdUserId(workspaceId, targetUserId)
            .orElseThrow(
                () ->
                    Exceptions.notFound(
                        "WORKSPACE_MEMBER_NOT_FOUND", "Workspace member not found"));
    if (target.getRole() == WorkspaceRole.OWNER && ownerCount(workspaceId) <= 1) {
      throw Exceptions.conflict("LAST_OWNER_CANNOT_BE_REMOVED", "Last owner cannot be removed");
    }
    workspaceMemberRepository.delete(target);
    auditService.record(
        "WORKSPACE_MEMBER_REMOVED",
        user.userId(),
        workspaceId,
        "WORKSPACE_MEMBER",
        targetUserId,
        Map.of("role", target.getRole().name()));
  }

  Workspace loadWorkspace(UUID workspaceId) {
    return workspaceRepository
        .findByIdAndArchivedAtIsNull(workspaceId)
        .orElseThrow(() -> Exceptions.notFound("WORKSPACE_NOT_FOUND", "Workspace not found"));
  }

  private long ownerCount(UUID workspaceId) {
    return workspaceMemberRepository.countByIdWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER);
  }
}
