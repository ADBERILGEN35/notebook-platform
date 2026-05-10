package com.notebook.lumen.identity.admin.changerequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminChangeRequestRepository
    extends JpaRepository<PlatformAdminChangeRequest, UUID> {

  List<PlatformAdminChangeRequest> findTop100ByOrderByCreatedAtDesc();

  List<PlatformAdminChangeRequest> findTop100ByStatusOrderByCreatedAtDesc(ChangeRequestStatus status);

  Optional<PlatformAdminChangeRequest> findByIdAndRequestedByUserId(UUID id, UUID userId);
}
