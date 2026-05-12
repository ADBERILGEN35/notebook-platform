package com.notebook.lumen.identity.breakglass;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreakGlassTokenRotationEventRepository
    extends JpaRepository<BreakGlassTokenRotationEvent, UUID> {
  Optional<BreakGlassTokenRotationEvent> findByRotationKey(String rotationKey);

  Page<BreakGlassTokenRotationEvent> findByStatusInOrderByRequiredAtDesc(
      List<BreakGlassTokenRotationEventStatus> statuses, Pageable pageable);

  Page<BreakGlassTokenRotationEvent> findAllByOrderByRequiredAtDesc(Pageable pageable);

  long countByStatus(BreakGlassTokenRotationEventStatus status);

  List<BreakGlassTokenRotationEvent> findByStatusAndOldTokenHashFingerprint(
      BreakGlassTokenRotationEventStatus status, String oldTokenHashFingerprint);

  Optional<BreakGlassTokenRotationEvent> findTopByStatusOrderByVerifiedAtDesc(
      BreakGlassTokenRotationEventStatus status);

  Optional<BreakGlassTokenRotationEvent> findTopByOrderByRequiredAtDesc();

  Optional<BreakGlassTokenRotationEvent> findTopByStatusInOrderByRequiredAtAsc(
      List<BreakGlassTokenRotationEventStatus> statuses);

  long countByRequiredAtAfter(Instant threshold);
}
