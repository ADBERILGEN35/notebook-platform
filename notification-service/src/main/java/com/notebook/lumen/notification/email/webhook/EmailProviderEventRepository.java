package com.notebook.lumen.notification.email.webhook;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailProviderEventRepository extends JpaRepository<EmailProviderEvent, UUID> {
  Optional<EmailProviderEvent> findByProviderAndProviderEventId(
      String provider, String providerEventId);
}
