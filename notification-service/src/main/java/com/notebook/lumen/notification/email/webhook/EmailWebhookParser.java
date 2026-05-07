package com.notebook.lumen.notification.email.webhook;

import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EmailWebhookParser {
  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  public List<EmailProviderWebhookEvent> parse(String provider, String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      List<EmailProviderWebhookEvent> events = new ArrayList<>();
      if (root.isArray()) {
        for (JsonNode item : root) {
          events.add(event(provider, item, body));
        }
      } else {
        events.add(event(provider, root, body));
      }
      return events;
    } catch (RuntimeException e) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "EMAIL_WEBHOOK_EVENT_INVALID", "Email webhook event invalid");
    }
  }

  private EmailProviderWebhookEvent event(String provider, JsonNode node, String body) {
    String eventType = firstText(node, "eventType", "event", "type");
    String messageId = firstText(node, "providerMessageId", "messageId", "sg_message_id", "id");
    String eventId = firstText(node, "providerEventId", "eventId", "sg_event_id");
    if (!hasText(eventId)) {
      eventId =
          provider
              + ":"
              + nullToUnknown(messageId)
              + ":"
              + nullToUnknown(eventType)
              + ":"
              + UUID.randomUUID();
    }
    Instant occurredAt = occurredAt(node);
    return new EmailProviderWebhookEvent(
        eventId,
        messageId,
        normalize(eventType),
        firstText(node, "recipientEmail", "email", "to"),
        occurredAt,
        sanitize(body));
  }

  private EmailProviderEventType normalize(String value) {
    if (!hasText(value)) {
      return EmailProviderEventType.UNKNOWN;
    }
    return switch (value.trim().toLowerCase()) {
      case "delivered", "delivery", "processed" -> EmailProviderEventType.DELIVERED;
      case "bounce", "bounced", "dropped" -> EmailProviderEventType.BOUNCE;
      case "complaint", "spamreport", "spam_report" -> EmailProviderEventType.COMPLAINT;
      default -> EmailProviderEventType.UNKNOWN;
    };
  }

  private Instant occurredAt(JsonNode node) {
    String value = firstText(node, "occurredAt", "timestamp", "createdAt");
    if (!hasText(value)) {
      return Instant.now();
    }
    try {
      if (value.matches("\\d+")) {
        return Instant.ofEpochSecond(Long.parseLong(value));
      }
      return Instant.parse(value);
    } catch (RuntimeException e) {
      return Instant.now();
    }
  }

  private String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      JsonNode value = node.get(field);
      if (value != null && !value.isNull() && hasText(value.asText())) {
        return value.asText();
      }
    }
    return null;
  }

  private String sanitize(String value) {
    if (value == null) {
      return "{}";
    }
    String sanitized =
        value.replaceAll(
            "(?i)\"(api[_-]?key|token|authorization|secret|signature)\"\\s*:\\s*\"[^\"]*\"",
            "\"$1\":\"***\"");
    return sanitized.length() <= 4000 ? sanitized : sanitized.substring(0, 4000);
  }

  private String nullToUnknown(String value) {
    return hasText(value) ? value : "unknown";
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
