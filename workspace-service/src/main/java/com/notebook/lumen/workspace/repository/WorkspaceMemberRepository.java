package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.WorkspaceMember;
import com.notebook.lumen.workspace.domain.WorkspaceMemberId;
import com.notebook.lumen.workspace.domain.WorkspaceRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository
    extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {
  Optional<WorkspaceMember> findByIdWorkspaceIdAndIdUserId(UUID workspaceId, UUID userId);

  List<WorkspaceMember> findByIdUserId(UUID userId);

  List<WorkspaceMember> findByIdWorkspaceId(UUID workspaceId);

  long countByIdWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

  boolean existsByIdWorkspaceIdAndIdUserId(UUID workspaceId, UUID userId);
}
