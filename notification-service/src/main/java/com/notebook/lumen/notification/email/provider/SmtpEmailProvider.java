package com.notebook.lumen.notification.email.provider;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class SmtpEmailProvider implements EmailProvider {
  private final JavaMailSender mailSender;
  private final String from;

  public SmtpEmailProvider(JavaMailSender mailSender, String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  public String providerName() {
    return "smtp";
  }

  @Override
  public boolean supportsWebhooks() {
    return false;
  }

  @Override
  public EmailSendResult send(EmailMessage message) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(message.recipient());
      helper.setSubject(message.subject());
      if (hasText(message.bodyHtml())) {
        helper.setText(message.bodyText() == null ? "" : message.bodyText(), message.bodyHtml());
      } else {
        helper.setText(message.bodyText() == null ? "" : message.bodyText(), false);
      }
      mailSender.send(mimeMessage);
      return new EmailSendResult(
          providerName(), "smtp-" + UUID.randomUUID(), Instant.now(), "accepted", null);
    } catch (MessagingException | RuntimeException e) {
      throw new EmailProviderException("SMTP email send failed", e);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
