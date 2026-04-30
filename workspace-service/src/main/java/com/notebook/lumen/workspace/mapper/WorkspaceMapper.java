package com.notebook.lumen.workspace.mapper;

import com.notebook.lumen.workspace.domain.*;
import com.notebook.lumen.workspace.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {
  WorkspaceResponse toResponse(Workspace workspace);

  WorkspaceMemberResponse toResponse(WorkspaceMember member);

  NotebookResponse toResponse(Notebook notebook);

  NotebookMemberResponse toResponse(NotebookMember member);

  TagResponse toResponse(Tag tag);

  @Mapping(target = "inviteToken", ignore = true)
  @Mapping(target = "acceptUrl", ignore = true)
  InvitationResponse toResponse(Invitation invitation);
}
