package com.notebook.lumen.identity.scim.sync;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScimSyncRunRepository extends JpaRepository<ScimSyncRun, UUID> {
  @Query(
      "SELECT r FROM ScimSyncRun r WHERE "
          + "(:provider IS NULL OR r.provider = :provider) AND "
          + "(:resourceType IS NULL OR r.resourceType = :resourceType) AND "
          + "(:status IS NULL OR r.status = :status) AND "
          + "(:from IS NULL OR r.startedAt >= :from) AND "
          + "(:to IS NULL OR r.startedAt <= :to)")
  Page<ScimSyncRun> search(
      @Param("provider") String provider,
      @Param("resourceType") ScimResourceType resourceType,
      @Param("status") ScimSyncRunStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
