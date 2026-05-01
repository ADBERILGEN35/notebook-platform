package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.Notebook;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotebookRepository extends JpaRepository<Notebook, UUID> {
  List<Notebook> findByWorkspaceIdAndArchivedAtIsNull(UUID workspaceId);

  Page<Notebook> findByWorkspaceIdAndArchivedAtIsNull(UUID workspaceId, Pageable pageable);

  Optional<Notebook> findByIdAndArchivedAtIsNull(UUID id);
}
