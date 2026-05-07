package com.notebook.lumen.notification.email.suppression;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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
      """)
  Optional<EmailSuppression> findActive(@Param("email") String email, @Param("now") Instant now);
}
