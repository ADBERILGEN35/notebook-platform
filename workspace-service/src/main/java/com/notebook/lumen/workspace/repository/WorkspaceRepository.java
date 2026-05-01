package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.Workspace;
import com.notebook.lumen.workspace.domain.WorkspaceType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
  boolean existsBySlug(String slug);

  boolean existsByOwnerIdAndTypeAndArchivedAtIsNull(UUID ownerId, WorkspaceType type);

  Optional<Workspace> findByIdAndArchivedAtIsNull(UUID id);

  @Query(
      """
      select w from Workspace w
      join WorkspaceMember m on m.id.workspaceId = w.id
      where m.id.userId = :userId and w.archivedAt is null
      """)
  Page<Workspace> findActiveByMemberUserId(UUID userId, Pageable pageable);
}
