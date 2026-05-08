package com.notebook.lumen.notification.user.fanout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationFanoutOutboxRepository extends JpaRepository<NotificationFanoutOutbox, UUID> {
  boolean existsByEventId(UUID eventId);

  long countByStatus(NotificationFanoutOutboxStatus status);

  @Query(
      value =
          """
          SELECT *
          FROM notification_fanout_outbox
          WHERE status = :status
            AND next_attempt_at <= :now
          ORDER BY created_at ASC
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<NotificationFanoutOutbox> findDuePendingForUpdate(
      @Param("status") String status, @Param("now") Instant now, @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT *
          FROM notification_fanout_outbox
          WHERE status = :status
            AND lock_expires_at IS NOT NULL
            AND lock_expires_at <= :now
          ORDER BY lock_expires_at ASC
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<NotificationFanoutOutbox> findExpiredSendingForUpdate(
      @Param("status") String status, @Param("now") Instant now, @Param("limit") int limit);
}
