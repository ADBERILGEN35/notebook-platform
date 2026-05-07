package com.notebook.lumen.notification.email.provider;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogEmailProvider implements EmailProvider {
  private static final Logger log = LoggerFactory.getLogger(LogEmailProvider.class);

  @Override
  public String providerName() {
    return "log";
  }

  @Override
  public boolean supportsWebhooks() {
    return false;
  }

  @Override
  public EmailSendResult send(EmailMessage message) {
    log.info(
        "Email accepted by log provider recipient={} subject={} metadata={}",
        message.recipient(),
        message.subject(),
        message.metadata());
    return new EmailSendResult(
        providerName(), "log-" + UUID.randomUUID(), Instant.now(), "accepted", null);
  }
}
