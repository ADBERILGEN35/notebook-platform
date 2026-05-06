package com.notebook.lumen.notification.email.provider;

import java.time.Instant;
import java.util.UUID;

public class NoopEmailProvider implements EmailProvider {
  @Override
  public EmailSendResult send(EmailMessage message) {
    return new EmailSendResult("noop", "noop-" + UUID.randomUUID(), Instant.now());
  }
}
