package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.Tag;
import com.notebook.lumen.workspace.domain.TagScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, UUID> {
  List<Tag> findByWorkspaceIdAndArchivedAtIsNull(UUID workspaceId);

  Optional<Tag> findByIdAndArchivedAtIsNull(UUID id);

  boolean existsByIdAndWorkspaceIdAndScopeAndArchivedAtIsNull(
      UUID id, UUID workspaceId, TagScope scope);

  @Query(
      "select count(t) > 0 from Tag t where t.workspaceId = :workspaceId and lower(t.name) = lower(:name) and t.scope = :scope and t.archivedAt is null")
  boolean existsActiveByWorkspaceNameAndScope(UUID workspaceId, String name, TagScope scope);
}
