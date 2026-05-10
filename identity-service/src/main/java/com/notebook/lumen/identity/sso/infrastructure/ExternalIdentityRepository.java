package com.notebook.lumen.identity.sso.infrastructure;

import com.notebook.lumen.identity.sso.domain.ExternalIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {
  Optional<ExternalIdentity> findByProviderAndSubject(String provider, String subject);

  List<ExternalIdentity> findByUser_IdOrderByLastLoginAtDesc(UUID userId, Pageable pageable);
}
