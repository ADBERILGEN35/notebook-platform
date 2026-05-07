package com.notebook.lumen.notification.email.webhook;

import org.springframework.http.HttpHeaders;

public interface EmailWebhookVerifier {
  boolean verify(String provider, HttpHeaders headers, String body);
}
