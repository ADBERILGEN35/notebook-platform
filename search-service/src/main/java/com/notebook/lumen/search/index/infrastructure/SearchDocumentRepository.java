package com.notebook.lumen.search.index.infrastructure;

import com.notebook.lumen.search.index.domain.SearchDocument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchDocumentRepository extends JpaRepository<SearchDocument, UUID> {
  Optional<SearchDocument> findByNoteId(UUID noteId);

  @Query(
      value =
          """
          SELECT sd.workspace_id AS workspaceId,
                 sd.notebook_id AS notebookId,
                 sd.note_id AS noteId,
                 sd.title AS title,
                 sd.note_updated_at AS noteUpdatedAt,
                 ts_rank_cd(sd.search_vector, plainto_tsquery('simple', :query)) AS rank
          FROM search_documents sd
          WHERE sd.workspace_id = :workspaceId
            AND sd.archived_at IS NULL
            AND (:notebookId IS NULL OR sd.notebook_id = :notebookId)
            AND (
              sd.search_vector @@ plainto_tsquery('simple', :query)
              OR lower(sd.title) LIKE lower(concat('%', :query, '%'))
            )
          ORDER BY rank DESC, sd.note_updated_at DESC NULLS LAST, sd.updated_at DESC
          """,
      countQuery =
          """
          SELECT count(*)
          FROM search_documents sd
          WHERE sd.workspace_id = :workspaceId
            AND sd.archived_at IS NULL
            AND (:notebookId IS NULL OR sd.notebook_id = :notebookId)
            AND (
              sd.search_vector @@ plainto_tsquery('simple', :query)
              OR lower(sd.title) LIKE lower(concat('%', :query, '%'))
            )
          """,
      nativeQuery = true)
  Page<SearchDocumentSearchRow> search(
      @Param("workspaceId") UUID workspaceId,
      @Param("notebookId") UUID notebookId,
      @Param("query") String query,
      Pageable pageable);

  @Modifying
  @Query(
      value =
          """
          UPDATE search_documents
          SET archived_at = :now,
              updated_at = :now
          WHERE archived_at IS NULL
            AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
            AND (:notebookId IS NULL OR notebook_id = :notebookId)
            AND (
              last_seen_reindex_job_id IS NULL
              OR last_seen_reindex_job_id <> :jobId
            )
          """,
      nativeQuery = true)
  int archiveActiveOrphansForReindex(
      @Param("jobId") UUID jobId,
      @Param("workspaceId") UUID workspaceId,
      @Param("notebookId") UUID notebookId,
      @Param("now") java.time.Instant now);
}
