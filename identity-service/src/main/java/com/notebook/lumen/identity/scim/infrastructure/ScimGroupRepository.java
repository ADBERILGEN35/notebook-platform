package com.notebook.lumen.identity.scim.infrastructure;

import com.notebook.lumen.identity.scim.domain.ScimGroup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScimGroupRepository extends JpaRepository<ScimGroup, UUID> {
  Optional<ScimGroup> findByExternalId(String externalId);

  Page<ScimGroup> findAllByActiveIsTrue(Pageable pageable);

  Optional<ScimGroup> findByIdAndActiveIsTrue(UUID id);
}
