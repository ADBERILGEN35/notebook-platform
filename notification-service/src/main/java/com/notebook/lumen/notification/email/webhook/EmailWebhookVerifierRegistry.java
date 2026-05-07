package com.notebook.lumen.notification.email.webhook;

import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EmailWebhookVerifierRegistry {
  private final List<EmailWebhookVerifier> verifiers;

  public EmailWebhookVerifierRegistry(List<EmailWebhookVerifier> verifiers) {
    this.verifiers = verifiers;
  }

  public EmailWebhookVerifier verifierFor(String provider) {
    return verifiers.stream()
        .filter(verifier -> verifier.supports(provider))
        .findFirst()
        .orElseThrow(
            () ->
                new NotificationException(
                    HttpStatus.BAD_REQUEST,
                    "EMAIL_WEBHOOK_EVENT_INVALID",
                    "No email webhook verifier configured for provider " + provider));
  }
}
