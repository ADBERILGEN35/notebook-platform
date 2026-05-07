package com.notebook.lumen.notification.email.provider;

import com.notebook.lumen.notification.email.provider.http.GenericHttpEmailProvider;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class EmailProviderConfig {
  @Bean
  EmailProvider emailProvider(NotificationProperties properties) {
    String provider = properties.email().provider() == null ? "log" : properties.email().provider();
    return switch (provider.toLowerCase()) {
      case "noop" -> new NoopEmailProvider();
      case "smtp" -> new SmtpEmailProvider(javaMailSender(properties), properties.email().from());
      case "generic-http" -> new GenericHttpEmailProvider(properties.email().genericHttp(), "generic-http");
      case "sendgrid" -> new GenericHttpEmailProvider(properties.email().genericHttp(), "sendgrid");
      case "log" -> new LogEmailProvider();
      default -> throw new IllegalStateException("Unsupported EMAIL_PROVIDER: " + provider);
    };
  }

  private JavaMailSender javaMailSender(NotificationProperties properties) {
    NotificationProperties.Smtp smtp = properties.email().smtp();
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(smtp.host());
    sender.setPort(smtp.port());
    sender.setUsername(smtp.username());
    sender.setPassword(smtp.password());
    Properties javaMailProperties = sender.getJavaMailProperties();
    javaMailProperties.put("mail.smtp.auth", Boolean.toString(hasText(smtp.username())));
    javaMailProperties.put("mail.smtp.starttls.enable", Boolean.toString(smtp.tlsEnabled()));
    return sender;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
