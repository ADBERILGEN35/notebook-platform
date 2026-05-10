package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.NoteMergeIdempotencyKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteMergeIdempotencyKeyRepository extends JpaRepository<NoteMergeIdempotencyKey, UUID> {
  Optional<NoteMergeIdempotencyKey> findByUserIdAndNoteIdAndIdempotencyKey(
      UUID userId, UUID noteId, String idempotencyKey);

  void deleteByCreatedAtBefore(Instant threshold);
}
