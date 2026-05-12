package com.notebook.lumen.identity.scim.sync;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScimSyncCheckpointRepository extends JpaRepository<ScimSyncCheckpoint, UUID> {
  Optional<ScimSyncCheckpoint> findByProviderAndResourceType(
      String provider, ScimResourceType resourceType);

  List<ScimSyncCheckpoint> findAllByOrderByProviderAscResourceTypeAsc();
}
