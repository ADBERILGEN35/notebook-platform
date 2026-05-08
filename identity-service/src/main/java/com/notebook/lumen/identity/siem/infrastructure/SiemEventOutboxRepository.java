package com.notebook.lumen.identity.siem.infrastructure;

import com.notebook.lumen.identity.siem.domain.SiemEventOutbox;
import com.notebook.lumen.identity.siem.domain.SiemOutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiemEventOutboxRepository extends JpaRepository<SiemEventOutbox, UUID> {
  @Query(
      value =
          """
          SELECT * FROM siem_event_outbox
          WHERE status = :status
            AND next_attempt_at <= :now
          ORDER BY created_at ASC
          FOR UPDATE SKIP LOCKED
          LIMIT :batchSize
          """,
      nativeQuery = true)
  List<SiemEventOutbox> lockDueEvents(
      @Param("status") String status, @Param("now") Instant now, @Param("batchSize") int batchSize);

  long countByStatus(SiemOutboxStatus status);
}
