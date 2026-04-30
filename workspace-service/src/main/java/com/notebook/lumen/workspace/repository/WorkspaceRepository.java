package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.Workspace;
import com.notebook.lumen.workspace.domain.WorkspaceType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
  boolean existsBySlug(String slug);

  boolean existsByOwnerIdAndTypeAndArchivedAtIsNull(UUID ownerId, WorkspaceType type);

  Optional<Workspace> findByIdAndArchivedAtIsNull(UUID id);
}
