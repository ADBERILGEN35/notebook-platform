package com.notebook.lumen.identity.admin.retention;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformLegalHoldRepository extends JpaRepository<PlatformLegalHold, UUID> {
  boolean existsByHoldKey(String holdKey);

  long countByStatus(PlatformLegalHoldStatus status);

  List<PlatformLegalHold> findByStatusOrderByCreatedAtDesc(PlatformLegalHoldStatus status);

  List<PlatformLegalHold> findAllByOrderByCreatedAtDesc();
}
