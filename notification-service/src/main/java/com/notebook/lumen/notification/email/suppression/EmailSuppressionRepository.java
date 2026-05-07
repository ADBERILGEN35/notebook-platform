package com.notebook.lumen.notification.email.suppression;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, UUID> {
  @Query(
      """
      select s
      from EmailSuppression s
      where lower(s.email) = lower(:email)
        and (s.expiresAt is null or s.expiresAt > :now)
        and s.releasedAt is null
      """)
  Optional<EmailSuppression> findActive(@Param("email") String email, @Param("now") Instant now);

  @Query(
      """
      select s
      from EmailSuppression s
      where lower(s.email) = lower(:email)
      """)
  Optional<EmailSuppression> findByNormalizedEmail(@Param("email") String email);

  @Query(
      """
      select s
      from EmailSuppression s
      where (:email is null or lower(s.email) = lower(:email))
        and (:reason is null or s.reason = :reason)
        and (
          :activeOnly = false
          or (s.releasedAt is null and (s.expiresAt is null or s.expiresAt > :now))
        )
      """)
  Page<EmailSuppression> search(
      @Param("email") String email,
      @Param("reason") EmailSuppressionReason reason,
      @Param("activeOnly") boolean activeOnly,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      """
      select count(s)
      from EmailSuppression s
      where s.reason = :reason
        and s.releasedAt is null
        and (s.expiresAt is null or s.expiresAt > :now)
      """)
  long countActiveByReason(
      @Param("reason") EmailSuppressionReason reason, @Param("now") Instant now);
}
