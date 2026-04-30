package com.notebook.lumen.workspace.service;

import com.notebook.lumen.workspace.domain.Notebook;
import com.notebook.lumen.workspace.domain.NotebookMember;
import com.notebook.lumen.workspace.domain.NotebookRole;
import com.notebook.lumen.workspace.domain.TagScope;
import com.notebook.lumen.workspace.domain.WorkspaceMember;
import com.notebook.lumen.workspace.domain.WorkspaceRole;
import com.notebook.lumen.workspace.dto.InternalResponses.NotebookPermissionResponse;
import com.notebook.lumen.workspace.dto.InternalResponses.TagExistsResponse;
import com.notebook.lumen.workspace.repository.NotebookMemberRepository;
import com.notebook.lumen.workspace.repository.NotebookRepository;
import com.notebook.lumen.workspace.repository.TagRepository;
import com.notebook.lumen.workspace.repository.WorkspaceMemberRepository;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import com.notebook.lumen.workspace.tenant.TenantDatabaseSession;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalWorkspaceService {
  private final NotebookRepository notebookRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final NotebookMemberRepository notebookMemberRepository;
  private final TagRepository tagRepository;
  private final TenantDatabaseSession tenantDatabaseSession;

  public InternalWorkspaceService(
      NotebookRepository notebookRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      NotebookMemberRepository notebookMemberRepository,
      TagRepository tagRepository,
      TenantDatabaseSession tenantDatabaseSession) {
    this.notebookRepository = notebookRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.notebookMemberRepository = notebookMemberRepository;
    this.tagRepository = tagRepository;
    this.tenantDatabaseSession = tenantDatabaseSession;
  }

  @Transactional(readOnly = true)
  public NotebookPermissionResponse notebookPermissions(UUID notebookId, UUID userId) {
    Notebook notebook =
        notebookRepository
            .findByIdAndArchivedAtIsNull(notebookId)
            .orElseThrow(() -> Exceptions.notFound("NOTEBOOK_NOT_FOUND", "Notebook not found"));
    tenantDatabaseSession.applyWorkspace(notebook.getWorkspaceId());

    return workspaceMemberRepository
        .findByIdWorkspaceIdAndIdUserId(notebook.getWorkspaceId(), userId)
        .map(member -> permissionsForMember(notebook, member))
        .orElseGet(
            () ->
                new NotebookPermissionResponse(
                    notebook.getWorkspaceId(), notebook.getId(), null, false, false, false, false));
  }

  @Transactional(readOnly = true)
  public TagExistsResponse tagExists(UUID workspaceId, UUID tagId, TagScope scope) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    boolean exists =
        tagRepository.existsByIdAndWorkspaceIdAndScopeAndArchivedAtIsNull(
            tagId, workspaceId, scope);
    return new TagExistsResponse(workspaceId, tagId, scope.name(), exists);
  }

  private NotebookPermissionResponse permissionsForMember(
      Notebook notebook, WorkspaceMember workspaceMember) {
    WorkspaceRole workspaceRole = workspaceMember.getRole();
    if (workspaceRole == WorkspaceRole.OWNER || workspaceRole == WorkspaceRole.ADMIN) {
      return fullAccess(notebook, workspaceRole.name());
    }

    return notebookMemberRepository
        .findByIdNotebookIdAndIdUserId(notebook.getId(), workspaceMember.getUserId())
        .map(member -> permissionsForNotebookRole(notebook, member))
        .orElseGet(
            () ->
                new NotebookPermissionResponse(
                    notebook.getWorkspaceId(),
                    notebook.getId(),
                    NotebookRole.VIEWER.name(),
                    true,
                    false,
                    false,
                    false));
  }

  private NotebookPermissionResponse permissionsForNotebookRole(
      Notebook notebook, NotebookMember notebookMember) {
    NotebookRole role = notebookMember.getRole();
    return switch (role) {
      case OWNER -> fullAccess(notebook, role.name());
      case EDITOR ->
          new NotebookPermissionResponse(
              notebook.getWorkspaceId(), notebook.getId(), role.name(), true, true, true, false);
      case COMMENTER ->
          new NotebookPermissionResponse(
              notebook.getWorkspaceId(), notebook.getId(), role.name(), true, false, true, false);
      case VIEWER ->
          new NotebookPermissionResponse(
              notebook.getWorkspaceId(), notebook.getId(), role.name(), true, false, false, false);
    };
  }

  private NotebookPermissionResponse fullAccess(Notebook notebook, String role) {
    return new NotebookPermissionResponse(
        notebook.getWorkspaceId(), notebook.getId(), role, true, true, true, true);
  }
}
