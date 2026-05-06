package com.notebook.lumen.search.reindex.infrastructure;

import com.notebook.lumen.search.reindex.domain.SearchReindexJob;
import com.notebook.lumen.search.reindex.domain.SearchReindexJobStatus;
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
}
