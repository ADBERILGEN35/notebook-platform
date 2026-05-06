package com.notebook.lumen.notification.email.provider;

public interface EmailProvider {
  EmailSendResult send(EmailMessage message);
}
