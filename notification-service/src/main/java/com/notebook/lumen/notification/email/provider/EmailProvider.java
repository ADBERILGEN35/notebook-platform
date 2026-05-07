package com.notebook.lumen.notification.email.provider;

public interface EmailProvider {
  String providerName();

  boolean supportsWebhooks();

  EmailSendResult send(EmailMessage message);
}
