package com.notebook.lumen.notification.email.provider;

import java.time.Instant;
import java.util.UUID;

public class NoopEmailProvider implements EmailProvider {
  @Override
  public String providerName() {
    return "noop";
  }

  @Override
  public boolean supportsWebhooks() {
    return false;
  }

  @Override
  public EmailSendResult send(EmailMessage message) {
    return new EmailSendResult(
        providerName(), "noop-" + UUID.randomUUID(), Instant.now(), "accepted", null);
  }
}
