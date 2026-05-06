package com.notebook.lumen.notification.email.infrastructure;

import com.notebook.lumen.notification.email.domain.EmailNotification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, UUID> {
  Optional<EmailNotification> findByIdempotencyKey(String idempotencyKey);

  @Query(
      value =
          """
          SELECT *
          FROM email_notifications
          WHERE status = :status
            AND next_attempt_at <= :now
          ORDER BY created_at ASC
          LIMIT 25
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<EmailNotification> findDueNotifications(
      @Param("status") String status, @Param("now") Instant now);
}
