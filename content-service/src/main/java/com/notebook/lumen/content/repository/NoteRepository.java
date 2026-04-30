package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<Note, UUID> {
  Optional<Note> findByIdAndArchivedAtIsNull(UUID id);

  List<Note> findByNotebookIdAndArchivedAtIsNull(UUID notebookId);

  boolean existsByIdAndWorkspaceIdAndArchivedAtIsNull(UUID id, UUID workspaceId);

  @Query(
      value =
          """
        select * from notes
        where workspace_id = :workspaceId
          and archived_at is null
          and (:q = '' or search_vector @@ plainto_tsquery('simple', :q) or lower(title) like lower(concat('%', :q, '%')))
        order by updated_at desc
        limit 50
        """,
      nativeQuery = true)
  List<Note> search(@Param("workspaceId") UUID workspaceId, @Param("q") String q);
}
