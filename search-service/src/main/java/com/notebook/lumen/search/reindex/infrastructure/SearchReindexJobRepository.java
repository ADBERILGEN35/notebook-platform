package com.notebook.lumen.search.reindex.infrastructure;

import com.notebook.lumen.search.reindex.domain.SearchReindexJob;
import com.notebook.lumen.search.reindex.domain.SearchReindexJobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchReindexJobRepository extends JpaRepository<SearchReindexJob, UUID> {
  boolean existsByStatusIn(Collection<SearchReindexJobStatus> statuses);

  long countByStatus(SearchReindexJobStatus status);

  @Query(
      value =
          """
          SELECT *
          FROM search_reindex_jobs
          WHERE status = :status
          ORDER BY created_at ASC
          LIMIT 1
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<SearchReindexJob> findNextForUpdate(@Param("status") String status);

  @Query(
      value =
          """
          SELECT *
          FROM search_reindex_jobs
          WHERE status = :status
            AND lock_expires_at IS NOT NULL
            AND lock_expires_at <= :now
          ORDER BY lock_expires_at ASC
          LIMIT 5
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<SearchReindexJob> findExpiredRunningForUpdate(
      @Param("status") String status, @Param("now") Instant now);
}
