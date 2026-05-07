package com.notebook.lumen.notification.email.webhook;

import org.springframework.http.HttpHeaders;

public interface EmailWebhookVerifier {
  boolean supports(String provider);

  EmailWebhookVerificationResult verify(HttpHeaders headers, String body);
}
