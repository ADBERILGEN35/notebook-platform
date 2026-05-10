package com.notebook.lumen.notification.admin.legalhold;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLegalHoldRepository extends JpaRepository<NotificationLegalHoldEntity, UUID> {

  long countByStatus(LegalHoldStatus status);

  long countByStatusAndScope(LegalHoldStatus status, LegalHoldScope scope);

  boolean existsByHoldKey(String holdKey);

  Optional<NotificationLegalHoldEntity> findByHoldKey(String holdKey);

  List<NotificationLegalHoldEntity> findByStatusOrderByCreatedAtDesc(LegalHoldStatus status);
}
