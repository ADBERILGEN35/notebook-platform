package com.notebook.lumen.gateway.admin.audit;

import com.notebook.lumen.gateway.config.GatewayAuditExportProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditExportService {
  private static final List<String> CSV_COLUMNS =
      List.of(
          "id",
          "source",
          "eventType",
          "actorUserId",
          "workspaceId",
          "aggregateType",
          "aggregateId",
          "requestId",
          "ipAddress",
          "userAgent",
          "metadataJson",
          "createdAt");
  private static final List<String> SENSITIVE_KEYS =
      List.of(
          "password",
          "token",
          "secret",
          "key",
          "authorization",
          "cookie",
          "private",
          "credential",
          "session",
          "jwt");

  private final AuditProxyService auditProxyService;
  private final GatewayAuditExportProperties properties;
  private final ObjectMapper objectMapper;

  public AuditExportService(AuditProxyService auditProxyService, GatewayAuditExportProperties properties) {
    this.auditProxyService = auditProxyService;
    this.properties = properties;
    this.objectMapper = JsonMapper.builder().findAndAddModules().build();
  }

  public Mono<AuditExportPayload> export(
      AuditSource source, String formatRaw, Map<String, String> normalizedFilters) {
    String format = normalizeFormat(formatRaw);
    Instant createdFrom = requireInstant(normalizedFilters.get("createdFrom"), "createdFrom");
    Instant createdTo = requireInstant(normalizedFilters.get("createdTo"), "createdTo");
    if (createdFrom.isAfter(createdTo)) {
      throw new AuditProxyException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.AUDIT_EXPORT_RANGE_REQUIRED,
          "createdFrom must be before or equal to createdTo");
    }
    long days = Duration.between(createdFrom, createdTo).toDays();
    if (days > properties.effectiveMaxRangeDays()) {
      throw new AuditProxyException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.AUDIT_EXPORT_RANGE_TOO_LARGE,
          "Export date range exceeds allowed limit");
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    return collectPages(source, normalizedFilters, 0, rows)
        .then(
            Mono.fromSupplier(
                () -> {
                  byte[] data = formatData(format, rows);
                  String filename =
                      "audit-"
                          + source.value()
                          + "-"
                          + LocalDate.now(ZoneOffset.UTC)
                          + "."
                          + ("csv".equals(format) ? "csv" : "jsonl");
                  String contentType =
                      "csv".equals(format) ? "text/csv; charset=utf-8" : "application/x-ndjson";
                  return new AuditExportPayload(
                      data, contentType, filename, rows.size(), createdFrom, createdTo, format);
                }));
  }

  private Mono<Void> collectPages(
      AuditSource source, Map<String, String> baseFilters, int page, List<Map<String, Object>> rows) {
    Map<String, String> params = new LinkedHashMap<>(baseFilters);
    params.put("page", Integer.toString(page));
    params.put("size", Integer.toString(properties.effectivePageSize()));

    return auditProxyService
        .proxy(source, params)
        .flatMap(
            body -> {
              try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode items = root.path("items");
                if (items.isArray()) {
                  for (JsonNode item : items) {
                    if (rows.size() >= properties.effectiveMaxRecords()) {
                      return Mono.error(
                          new AuditProxyException(
                              HttpStatus.BAD_REQUEST,
                              ErrorCode.AUDIT_EXPORT_TOO_LARGE,
                              "Export exceeds max record limit"));
                    }
                    rows.add(toSanitizedRow(source, item));
                  }
                }
                boolean hasNext = root.path("hasNext").asBoolean(!root.path("last").asBoolean(true));
                if (!hasNext) {
                  return Mono.empty();
                }
                return collectPages(source, baseFilters, page + 1, rows);
              } catch (AuditProxyException e) {
                return Mono.error(e);
              } catch (Exception e) {
                return Mono.error(
                    new AuditProxyException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        ErrorCode.AUDIT_EXPORT_FAILED,
                        "Audit export serialization failed"));
              }
            });
  }

  private Map<String, Object> toSanitizedRow(AuditSource source, JsonNode item) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", text(item, "id"));
    row.put("source", source.value());
    row.put("eventType", text(item, "eventType"));
    row.put("actorUserId", text(item, "actorUserId"));
    row.put("workspaceId", text(item, "workspaceId"));
    row.put("aggregateType", text(item, "aggregateType"));
    row.put("aggregateId", text(item, "aggregateId"));
    row.put("requestId", text(item, "requestId"));
    row.put("ipAddress", text(item, "ipAddress"));
    row.put("userAgent", text(item, "userAgent"));
    Object sanitizedMetadata = sanitizeValue("metadata", objectMapper.convertValue(item.path("metadata"), Object.class));
    row.put("metadata", sanitizedMetadata);
    row.put("createdAt", text(item, "createdAt"));
    return row;
  }

  private byte[] formatData(String format, List<Map<String, Object>> rows) {
    StringBuilder out = new StringBuilder();
    try {
      if ("csv".equals(format)) {
        out.append(String.join(",", CSV_COLUMNS)).append('\n');
        for (Map<String, Object> row : rows) {
          List<String> cells = new ArrayList<>(CSV_COLUMNS.size());
          for (String key : CSV_COLUMNS) {
            Object value =
                "metadataJson".equals(key)
                    ? objectMapper.writeValueAsString(row.get("metadata"))
                    : row.get(toRowKey(key));
            cells.add(csvCell(value == null ? "" : String.valueOf(value)));
          }
          out.append(String.join(",", cells)).append('\n');
        }
      } else {
        for (Map<String, Object> row : rows) {
          Map<String, Object> jsonl = new LinkedHashMap<>();
          jsonl.put("id", row.get("id"));
          jsonl.put("source", row.get("source"));
          jsonl.put("eventType", row.get("eventType"));
          jsonl.put("actorUserId", row.get("actorUserId"));
          jsonl.put("workspaceId", row.get("workspaceId"));
          jsonl.put("aggregateType", row.get("aggregateType"));
          jsonl.put("aggregateId", row.get("aggregateId"));
          jsonl.put("requestId", row.get("requestId"));
          jsonl.put("ipAddress", row.get("ipAddress"));
          jsonl.put("userAgent", row.get("userAgent"));
          jsonl.put("metadata", row.get("metadata"));
          jsonl.put("createdAt", row.get("createdAt"));
          out.append(objectMapper.writeValueAsString(jsonl)).append('\n');
        }
      }
      return out.toString().getBytes(StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AuditProxyException(
          HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AUDIT_EXPORT_FAILED, "Failed to format export payload");
    }
  }

  private String toRowKey(String csvColumn) {
    return switch (csvColumn) {
      case "metadataJson" -> "metadata";
      default -> csvColumn;
    };
  }

  private String csvCell(String raw) {
    String value = raw == null ? "" : raw;
    if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
      value = "'" + value;
    }
    String escaped = value.replace("\"", "\"\"");
    return "\"" + escaped + "\"";
  }

  private String normalizeFormat(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new AuditProxyException(
          HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AUDIT_EXPORT_FORMAT, "Export format is required");
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    if (!value.equals("csv") && !value.equals("jsonl")) {
      throw new AuditProxyException(
          HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AUDIT_EXPORT_FORMAT, "Invalid audit export format");
    }
    return value;
  }

  private Instant requireInstant(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new AuditProxyException(
          HttpStatus.BAD_REQUEST, ErrorCode.AUDIT_EXPORT_RANGE_REQUIRED, field + " is required");
    }
    try {
      return Instant.parse(value);
    } catch (Exception e) {
      throw new AuditProxyException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.AUDIT_EXPORT_RANGE_REQUIRED,
          field + " must be ISO-8601 timestamp");
    }
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return "";
    }
    return value.asText("");
  }

  private Object sanitizeValue(String key, Object value) {
    if (value == null) {
      return null;
    }
    String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
    for (String sensitive : SENSITIVE_KEYS) {
      if (normalizedKey.contains(sensitive)) {
        return "***masked***";
      }
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sanitized = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String childKey = String.valueOf(entry.getKey());
        sanitized.put(childKey, sanitizeValue(childKey, entry.getValue()));
      }
      return sanitized;
    }
    if (value instanceof List<?> list) {
      List<Object> sanitized = new ArrayList<>(list.size());
      for (Object item : list) {
        sanitized.add(sanitizeValue(key, item));
      }
      return sanitized;
    }
    if (value instanceof String stringValue && stringValue.length() > 5000) {
      return stringValue.substring(0, 5000);
    }
    return value;
  }

  public record AuditExportPayload(
      byte[] bytes,
      String contentType,
      String fileName,
      int exportedCount,
      Instant createdFrom,
      Instant createdTo,
      String format) {}
}
