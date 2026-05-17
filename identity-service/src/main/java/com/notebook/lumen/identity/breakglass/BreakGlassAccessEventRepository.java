package com.notebook.lumen.identity.breakglass;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreakGlassAccessEventRepository
    extends JpaRepository<BreakGlassAccessEvent, UUID> {
  Optional<BreakGlassAccessEvent> findBySessionId(String sessionId);

  Optional<BreakGlassAccessEvent> findByTokenJti(String tokenJti);

  java.util.List<BreakGlassAccessEvent> findByExpiresAtAfterAndTokenJtiIsNotNull(Instant now);

  Page<BreakGlassAccessEvent> findByStatusAndIssuedAtBetween(
      BreakGlassAccessEventStatus status, Instant from, Instant to, Pageable pageable);

  Page<BreakGlassAccessEvent> findByIssuedAtBetween(Instant from, Instant to, Pageable pageable);

  long countByStatusAndIssuedAtBefore(BreakGlassAccessEventStatus status, Instant threshold);

  long countByStatus(BreakGlassAccessEventStatus status);
}
