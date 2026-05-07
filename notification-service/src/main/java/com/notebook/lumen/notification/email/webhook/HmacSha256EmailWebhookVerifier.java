package com.notebook.lumen.notification.email.webhook;

import com.notebook.lumen.notification.shared.config.NotificationProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class HmacSha256EmailWebhookVerifier implements EmailWebhookVerifier {
  private final NotificationProperties properties;

  public HmacSha256EmailWebhookVerifier(NotificationProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean supports(String provider) {
    return provider != null
        && (provider.equalsIgnoreCase("generic-http")
            || provider.equalsIgnoreCase("sendgrid")
            || provider.equalsIgnoreCase(properties.email().webhooks().provider()));
  }

  @Override
  public EmailWebhookVerificationResult verify(HttpHeaders headers, String body) {
    var webhooks = properties.email().webhooks();
    if (!hasText(webhooks.secret())) {
      return webhooks.allowNoopVerifier()
          ? EmailWebhookVerificationResult.accepted()
          : EmailWebhookVerificationResult.rejected();
    }
    String signature = headers.getFirst(webhooks.signatureHeader());
    if (!hasText(signature)) {
      return EmailWebhookVerificationResult.rejected();
    }
    String timestamp =
        hasText(webhooks.timestampHeader()) ? headers.getFirst(webhooks.timestampHeader()) : null;
    if (!hasText(timestamp) && webhooks.requireTimestamp()) {
      return EmailWebhookVerificationResult.rejectedAsReplay();
    }
    if (hasText(timestamp) && replay(timestamp, webhooks.toleranceSeconds())) {
      return EmailWebhookVerificationResult.rejectedAsReplay();
    }
    String payload = hasText(timestamp) ? timestamp + "." + body : body;
    String expected = hmac(webhooks.secret(), payload);
    String normalized =
        signature.startsWith("sha256=") ? signature.substring("sha256=".length()) : signature;
    return java.security.MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), normalized.getBytes(StandardCharsets.UTF_8))
        ? EmailWebhookVerificationResult.accepted()
        : EmailWebhookVerificationResult.rejected();
  }

  private boolean replay(String timestamp, long toleranceSeconds) {
    try {
      long value = Long.parseLong(timestamp);
      long now = Instant.now().getEpochSecond();
      long tolerance = toleranceSeconds <= 0 ? 300 : toleranceSeconds;
      return Math.abs(now - value) > tolerance;
    } catch (RuntimeException e) {
      return true;
    }
  }

  private String hmac(String secret, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to verify email webhook signature");
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
