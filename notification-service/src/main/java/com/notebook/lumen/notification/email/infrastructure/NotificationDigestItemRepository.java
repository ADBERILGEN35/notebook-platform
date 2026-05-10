package com.notebook.lumen.notification.email.infrastructure;

import com.notebook.lumen.notification.email.domain.NotificationDigestItem;
import com.notebook.lumen.notification.email.domain.NotificationDigestItemStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDigestItemRepository extends JpaRepository<NotificationDigestItem, UUID> {

  long countByStatus(NotificationDigestItemStatus status);

  @Query(
      value =
          """
          SELECT *
          FROM notification_digest_items
          WHERE status = :status
            AND scheduled_for <= :now
          ORDER BY scheduled_for ASC
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<NotificationDigestItem> findDueForUpdate(
      @Param("status") String status, @Param("now") Instant now, @Param("limit") int limit);
}
