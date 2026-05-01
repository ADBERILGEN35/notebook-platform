package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<Note, UUID> {
  Optional<Note> findByIdAndArchivedAtIsNull(UUID id);

  List<Note> findByNotebookIdAndArchivedAtIsNull(UUID notebookId);

  Page<Note> findByNotebookIdAndArchivedAtIsNull(UUID notebookId, Pageable pageable);

  boolean existsByIdAndWorkspaceIdAndArchivedAtIsNull(UUID id, UUID workspaceId);

  @Query(
      """
      select n from Note n
      where n.workspaceId = :workspaceId
        and n.archivedAt is null
        and (:q = '' or lower(n.title) like lower(concat('%', :q, '%')))
      """)
  Page<Note> search(
      @Param("workspaceId") UUID workspaceId, @Param("q") String q, Pageable pageable);
}
