package com.notebook.lumen.workspace.dto;

import com.notebook.lumen.workspace.domain.NotebookRole;
import com.notebook.lumen.workspace.domain.TagScope;
import com.notebook.lumen.workspace.domain.WorkspaceRole;
import com.notebook.lumen.workspace.domain.WorkspaceType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class Requests {
  private Requests() {}

  public record CreateWorkspaceRequest(
      @NotBlank String name,
      @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be lowercase kebab-case")
          String slug,
      @NotNull WorkspaceType type) {}

  public record UpdateWorkspaceRequest(
      @NotBlank String name,
      @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be lowercase kebab-case")
          String slug) {}

  public record UpdateWorkspaceMemberRoleRequest(@NotNull WorkspaceRole role) {}

  public record CreateNotebookRequest(@NotBlank String name, String icon) {}

  public record UpdateNotebookRequest(@NotBlank String name, String icon) {}

  public record UpsertNotebookMemberRequest(@NotNull NotebookRole role) {}

  public record UpdateNotebookMemberRoleRequest(@NotNull NotebookRole role) {}

  public record CreateTagRequest(
      @NotBlank String name,
      @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #22cc88")
          String color,
      @NotNull TagScope scope) {}

  public record UpdateTagRequest(
      @NotBlank String name,
      @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #22cc88")
          String color) {}

  public record CreateInvitationRequest(
      @NotBlank @Email String email, @NotNull WorkspaceRole role) {}

  public record AcceptInvitationRequest(@NotBlank String token) {}
}
