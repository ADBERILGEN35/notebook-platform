package com.notebook.lumen.identity.breakglass;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreakGlassTokenDenylistRepository
    extends JpaRepository<BreakGlassTokenDenylistEntry, UUID> {
  Optional<BreakGlassTokenDenylistEntry> findByJti(String jti);

  long countByExpiresAtAfter(Instant now);

  long deleteByExpiresAtBefore(Instant threshold);
}
