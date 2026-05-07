package com.notebook.lumen.notification.email.provider.http;

import com.notebook.lumen.notification.email.provider.EmailMessage;
import com.notebook.lumen.notification.email.provider.EmailProvider;
import com.notebook.lumen.notification.email.provider.EmailProviderException;
import com.notebook.lumen.notification.email.provider.EmailSendResult;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class GenericHttpEmailProvider implements EmailProvider {
  private final NotificationProperties.GenericHttp properties;
  private final String providerName;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public GenericHttpEmailProvider(
      NotificationProperties.GenericHttp properties,
      String providerName,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.providerName = providerName;
    this.meterRegistry = meterRegistry;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(effectiveConnectTimeoutMs()))
            .build();
    this.objectMapper = JsonMapper.builder().findAndAddModules().build();
  }

  @Override
  public String providerName() {
    return providerName;
  }

  @Override
  public boolean supportsWebhooks() {
    return true;
  }

  @Override
  public EmailSendResult send(EmailMessage message) {
    if (!hasText(properties.url())) {
      throw new EmailProviderException("EMAIL_GENERIC_HTTP_URL is required");
    }
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(properties.url()))
              .timeout(Duration.ofMillis(effectiveRequestTimeoutMs()))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body(message)));
      if (hasText(properties.apiKey())) {
        builder.header(
            hasText(properties.authorizationHeader())
                ? properties.authorizationHeader()
                : "Authorization",
            "Bearer " + properties.apiKey());
      }
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      meterRegistry
          .counter(
              "email_provider_response_status_total",
              "provider",
              providerName,
              "statusCode",
              Integer.toString(response.statusCode()))
          .increment();
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new EmailProviderException(
            "Generic HTTP email provider returned status " + response.statusCode());
      }
      return result(response.body());
    } catch (EmailProviderException e) {
      throw e;
    } catch (Exception e) {
      throw new EmailProviderException("Generic HTTP email provider request failed", e);
    }
  }

  private EmailSendResult result(String rawBody) {
    String providerMessageId = "generic-http-" + UUID.randomUUID();
    String status = "accepted";
    try {
      JsonNode root = objectMapper.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
      if (root.get("messageId") != null && !root.get("messageId").asText().isBlank()) {
        providerMessageId = root.get("messageId").asText();
      } else if (root.get("id") != null && !root.get("id").asText().isBlank()) {
        providerMessageId = root.get("id").asText();
      }
      if (root.get("status") != null && !root.get("status").asText().isBlank()) {
        status = root.get("status").asText();
      }
    } catch (RuntimeException ignored) {
      status = "accepted";
    }
    return new EmailSendResult(
        providerName(), providerMessageId, Instant.now(), status, sanitize(rawBody));
  }

  private String body(EmailMessage message) {
    return """
        {"to":"%s","subject":"%s","text":"%s","html":"%s","replyTo":"%s","metadata":{"notificationId":"%s","type":"%s"}}
        """
        .formatted(
            json(message.recipient()),
            json(message.subject()),
            json(message.bodyText()),
            json(message.bodyHtml()),
            json(message.replyTo()),
            json(message.metadata().getOrDefault("notificationId", "")),
            json(message.metadata().getOrDefault("type", "")));
  }

  private String sanitize(String value) {
    if (value == null) {
      return null;
    }
    String sanitized =
        value.replaceAll(
            "(?i)\"(api[_-]?key|token|authorization|secret)\"\\s*:\\s*\"[^\"]*\"",
            "\"$1\":\"***\"");
    return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
  }

  private String json(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }

  private long effectiveConnectTimeoutMs() {
    return properties.connectTimeoutMs() <= 0 ? 1000 : properties.connectTimeoutMs();
  }

  private long effectiveRequestTimeoutMs() {
    return properties.requestTimeoutMs() <= 0 ? 3000 : properties.requestTimeoutMs();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
