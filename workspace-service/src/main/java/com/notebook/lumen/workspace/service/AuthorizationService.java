package com.notebook.lumen.workspace.service;

import com.notebook.lumen.workspace.domain.*;
import com.notebook.lumen.workspace.repository.NotebookMemberRepository;
import com.notebook.lumen.workspace.repository.WorkspaceMemberRepository;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final NotebookMemberRepository notebookMemberRepository;

  public AuthorizationService(
      WorkspaceMemberRepository workspaceMemberRepository,
      NotebookMemberRepository notebookMemberRepository) {
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.notebookMemberRepository = notebookMemberRepository;
  }

  public WorkspaceMember requireWorkspaceMember(UUID workspaceId, UUID userId) {
    return workspaceMemberRepository
        .findByIdWorkspaceIdAndIdUserId(workspaceId, userId)
        .orElseThrow(
            () ->
                Exceptions.forbidden("WORKSPACE_ACCESS_DENIED", "User is not a workspace member"));
  }

  public WorkspaceMember requireWorkspaceRole(
      UUID workspaceId, UUID userId, WorkspaceRole... allowed) {
    WorkspaceMember member = requireWorkspaceMember(workspaceId, userId);
    for (WorkspaceRole role : allowed) {
      if (member.getRole() == role) {
        return member;
      }
    }
    throw Exceptions.forbidden("WORKSPACE_ACCESS_DENIED", "Insufficient workspace role");
  }

  public void requireNotebookRead(Notebook notebook, UUID userId) {
    WorkspaceMember member = requireWorkspaceMember(notebook.getWorkspaceId(), userId);
    if (member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN) {
      return;
    }
    notebookMemberRepository
        .findByIdNotebookIdAndIdUserId(notebook.getId(), userId)
        .orElseThrow(
            () -> Exceptions.forbidden("NOTEBOOK_ACCESS_DENIED", "User cannot access notebook"));
  }

  public void requireNotebookManage(Notebook notebook, UUID userId) {
    WorkspaceMember member = requireWorkspaceMember(notebook.getWorkspaceId(), userId);
    if (member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN) {
      return;
    }
    NotebookMember notebookMember =
        notebookMemberRepository
            .findByIdNotebookIdAndIdUserId(notebook.getId(), userId)
            .orElseThrow(
                () ->
                    Exceptions.forbidden("NOTEBOOK_ACCESS_DENIED", "User cannot manage notebook"));
    if (notebookMember.getRole() != NotebookRole.OWNER
        && notebookMember.getRole() != NotebookRole.EDITOR) {
      throw Exceptions.forbidden("NOTEBOOK_ACCESS_DENIED", "Insufficient notebook role");
    }
  }

  public void requireNotebookFullControl(Notebook notebook, UUID userId) {
    WorkspaceMember member = requireWorkspaceMember(notebook.getWorkspaceId(), userId);
    if (member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN) {
      return;
    }
    NotebookMember notebookMember =
        notebookMemberRepository
            .findByIdNotebookIdAndIdUserId(notebook.getId(), userId)
            .orElseThrow(
                () ->
                    Exceptions.forbidden("NOTEBOOK_ACCESS_DENIED", "User cannot manage notebook"));
    if (notebookMember.getRole() != NotebookRole.OWNER) {
      throw Exceptions.forbidden("NOTEBOOK_ACCESS_DENIED", "Notebook owner role is required");
    }
  }
}
