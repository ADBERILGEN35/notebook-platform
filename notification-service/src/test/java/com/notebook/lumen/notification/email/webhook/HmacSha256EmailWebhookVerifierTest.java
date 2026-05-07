package com.notebook.lumen.notification.email.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.notification.shared.config.NotificationProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class HmacSha256EmailWebhookVerifierTest {
  @Test
  void acceptsValidSignatureWithTimestamp() {
    String body = "{\"event\":\"delivered\"}";
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Email-Timestamp", timestamp);
    headers.set("X-Email-Signature", "sha256=" + hmac("secret", timestamp + "." + body));

    assertThat(verifier().verify("generic-http", headers, body)).isTrue();
  }

  @Test
  void rejectsInvalidSignature() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Email-Timestamp", String.valueOf(Instant.now().getEpochSecond()));
    headers.set("X-Email-Signature", "sha256=bad");

    assertThat(verifier().verify("generic-http", headers, "{}")).isFalse();
  }

  @Test
  void rejectsExpiredTimestamp() {
    String body = "{}";
    String timestamp = String.valueOf(Instant.now().minusSeconds(1000).getEpochSecond());
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Email-Timestamp", timestamp);
    headers.set("X-Email-Signature", "sha256=" + hmac("secret", timestamp + "." + body));

    assertThat(verifier().verify("generic-http", headers, body)).isFalse();
  }

  private HmacSha256EmailWebhookVerifier verifier() {
    return new HmacSha256EmailWebhookVerifier(properties());
  }

  private NotificationProperties properties() {
    return new NotificationProperties(
        "",
        new NotificationProperties.Email(
            "generic-http",
            "no-reply@example.com",
            true,
            5,
            60,
            3600,
            5000,
            25,
            300,
            new NotificationProperties.Smtp("localhost", 587, "", "", true),
            new NotificationProperties.GenericHttp("", "", "Authorization", 1000, 3000),
            new NotificationProperties.Webhooks(
                true,
                "generic-http",
                "secret",
                "X-Email-Signature",
                "X-Email-Timestamp",
                300,
                false)),
        new NotificationProperties.Internal(null, null));
  }

  private String hmac(String secret, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
