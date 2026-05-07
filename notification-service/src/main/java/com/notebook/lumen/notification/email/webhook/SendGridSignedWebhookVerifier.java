package com.notebook.lumen.notification.email.webhook;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class SendGridSignedWebhookVerifier implements EmailWebhookVerifier {
  @Override
  public boolean supports(String provider) {
    return false;
  }

  @Override
  public EmailWebhookVerificationResult verify(HttpHeaders headers, String body) {
    return EmailWebhookVerificationResult.rejected();
  }
}
