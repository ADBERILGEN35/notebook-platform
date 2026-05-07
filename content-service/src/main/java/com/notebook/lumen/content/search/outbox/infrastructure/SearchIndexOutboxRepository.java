package com.notebook.lumen.content.search.outbox.infrastructure;

import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEvent;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchIndexOutboxRepository extends JpaRepository<SearchIndexOutboxEvent, UUID> {
  Optional<SearchIndexOutboxEvent> findByIdempotencyKey(String idempotencyKey);

  long countByStatus(SearchIndexOutboxStatus status);

  @Query(
      value =
          """
          SELECT *
          FROM search_index_outbox
          WHERE status = :status
            AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
          ORDER BY created_at ASC
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<SearchIndexOutboxEvent> findDueForUpdate(
      @Param("status") String status, @Param("now") Instant now, @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT *
          FROM search_index_outbox
          WHERE status = :status
            AND lock_expires_at IS NOT NULL
            AND lock_expires_at <= :now
          ORDER BY lock_expires_at ASC
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<SearchIndexOutboxEvent> findStaleProcessingForUpdate(
      @Param("status") String status, @Param("now") Instant now, @Param("limit") int limit);

  @Query(
      """
      select e
      from SearchIndexOutboxEvent e
      where e.status = :status
        and (:workspaceId is null or e.workspaceId = :workspaceId)
        and (:noteId is null or e.noteId = :noteId)
      order by e.createdAt asc
      """)
  List<SearchIndexOutboxEvent> findFailedForReprocess(
      @Param("status") SearchIndexOutboxStatus status,
      @Param("workspaceId") UUID workspaceId,
      @Param("noteId") UUID noteId,
      Pageable pageable);

  @Query(
      """
      select min(e.createdAt)
      from SearchIndexOutboxEvent e
      where e.status = :status
      """)
  Optional<Instant> findOldestCreatedAtByStatus(@Param("status") SearchIndexOutboxStatus status);
}
