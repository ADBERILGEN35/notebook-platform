package com.notebook.lumen.gateway.admin.audit;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.net.ConnectException;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Service
public class AuditProxyService {
  static final String REQUIRED_SCOPE = "internal:audit:read";
  public static final String INTERNAL_AUTH_HEADER = "X-Service-Authorization";

  private final GatewayAuditProxyProperties properties;
  private final ServiceJwtSigner signer;
  private final WebClient webClient;

  public AuditProxyService(
      GatewayAuditProxyProperties properties,
      ServiceJwtSigner signer,
      WebClient.Builder webClientBuilder) {
    this.properties = properties;
    this.signer = signer;
    this.webClient = webClientBuilder.build();
  }

  public Mono<String> proxy(AuditSource source, Map<String, String> queryParams) {
    String baseUrl =
        switch (source) {
          case IDENTITY -> properties.identityServiceUrl();
          case WORKSPACE -> properties.workspaceServiceUrl();
          case CONTENT -> properties.contentServiceUrl();
        };
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new AuditProxyException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ErrorCode.AUDIT_SOURCE_UNAVAILABLE,
          "Audit source is unavailable");
    }

    String jwt;
    try {
      jwt = signer.sign(source.audience(), REQUIRED_SCOPE);
    } catch (RuntimeException e) {
      throw new AuditProxyException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ErrorCode.AUDIT_PROXY_REQUEST_FAILED,
          "Audit proxy unavailable");
    }

    return webClient
        .get()
        .uri(buildUri(baseUrl, queryParams))
        .accept(MediaType.APPLICATION_JSON)
        .header(INTERNAL_AUTH_HEADER, "Bearer " + jwt)
        .retrieve()
        .bodyToMono(String.class)
        .onErrorMap(this::mapError);
  }

  static Map<String, String> validateAndNormalize(Map<String, String> input) {
    Map<String, String> normalized = new LinkedHashMap<>();
    putIfNotBlank(normalized, "eventType", input.get("eventType"));
    putIfNotBlank(normalized, "actorUserId", validateUuid(input.get("actorUserId"), "actorUserId"));
    putIfNotBlank(normalized, "workspaceId", validateUuid(input.get("workspaceId"), "workspaceId"));
    putIfNotBlank(normalized, "aggregateType", input.get("aggregateType"));
    putIfNotBlank(normalized, "aggregateId", validateUuid(input.get("aggregateId"), "aggregateId"));
    putIfNotBlank(normalized, "requestId", input.get("requestId"));
    putIfNotBlank(
        normalized, "createdFrom", validateInstant(input.get("createdFrom"), "createdFrom"));
    putIfNotBlank(normalized, "createdTo", validateInstant(input.get("createdTo"), "createdTo"));
    normalized.put("page", validatePositiveInt(input.getOrDefault("page", "0"), 0, "page"));
    normalized.put("size", validatePositiveInt(input.getOrDefault("size", "50"), 200, "size"));
    normalized.put("sort", validateSort(input.getOrDefault("sort", "createdAt,desc")));

    if (normalized.containsKey("createdFrom") && normalized.containsKey("createdTo")) {
      Instant from = Instant.parse(normalized.get("createdFrom"));
      Instant to = Instant.parse(normalized.get("createdTo"));
      if (from.isAfter(to)) {
        throw invalidFilter("createdFrom must be before or equal to createdTo");
      }
    }
    return normalized;
  }

  private URI buildUri(String baseUrl, Map<String, String> params) {
    UriComponentsBuilder uriBuilder =
        UriComponentsBuilder.fromUriString(baseUrl).path(properties.effectiveInternalPath());
    params.forEach(uriBuilder::queryParam);
    return uriBuilder.build(true).toUri();
  }

  private Throwable mapError(Throwable throwable) {
    if (throwable instanceof WebClientResponseException responseException) {
      int status = responseException.getStatusCode().value();
      if (status == 401 || status == 403) {
        return new AuditProxyException(
            HttpStatus.BAD_GATEWAY,
            ErrorCode.AUDIT_PROXY_INTERNAL_AUTH_FAILED,
            "Internal audit authorization failed");
      }
      if (status == 503) {
        return new AuditProxyException(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCode.AUDIT_SOURCE_UNAVAILABLE,
            "Audit source is unavailable");
      }
      return new AuditProxyException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ErrorCode.AUDIT_PROXY_REQUEST_FAILED,
          "Audit proxy request failed");
    }
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof ConnectException) {
        return new AuditProxyException(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCode.AUDIT_SOURCE_UNAVAILABLE,
            "Audit source is unavailable");
      }
      current = current.getCause();
    }
    return new AuditProxyException(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.AUDIT_PROXY_REQUEST_FAILED,
        "Audit proxy request failed");
  }

  private static String validateUuid(String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value).toString();
    } catch (IllegalArgumentException e) {
      throw invalidFilter(field + " must be a valid UUID");
    }
  }

  private static String validateInstant(String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value).toString();
    } catch (DateTimeParseException e) {
      throw invalidFilter(field + " must be ISO-8601 timestamp");
    }
  }

  private static String validatePositiveInt(String value, int max, String field) {
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < 0) {
        throw invalidFilter(field + " must be >= 0");
      }
      if (max > 0 && parsed > max) {
        throw invalidFilter(field + " must be <= " + max);
      }
      return Integer.toString(parsed);
    } catch (NumberFormatException e) {
      throw invalidFilter(field + " must be an integer");
    }
  }

  private static String validateSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return "createdAt,desc";
    }
    String normalized = sort.trim();
    if (normalized.matches("^createdAt,(asc|desc)$")
        || normalized.matches("^eventType,(asc|desc)$")
        || normalized.matches("^aggregateType,(asc|desc)$")) {
      return normalized;
    }
    throw invalidFilter("sort is invalid");
  }

  private static void putIfNotBlank(Map<String, String> target, String key, String value) {
    if (value != null && !value.isBlank()) {
      target.put(key, value.trim());
    }
  }

  private static AuditProxyException invalidFilter(String message) {
    return new AuditProxyException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AUDIT_FILTER, message);
  }
}
