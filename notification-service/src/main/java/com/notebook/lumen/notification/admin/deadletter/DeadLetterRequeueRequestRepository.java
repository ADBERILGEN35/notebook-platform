package com.notebook.lumen.notification.admin.deadletter;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRequeueRequestRepository
    extends JpaRepository<DeadLetterRequeueRequestEntity, UUID> {

  Optional<DeadLetterRequeueRequestEntity> findBySourceAndDeadLetterIdAndIdempotencyKey(
      String source, UUID deadLetterId, String idempotencyKey);
}
