package com.notebook.lumen.identity.siem.application;

import com.notebook.lumen.identity.siem.SiemProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class GenericHttpSiemEventPublisher implements SiemEventPublisher {
  private final SiemProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public GenericHttpSiemEventPublisher(SiemProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.timeoutSeconds())).build();
  }

  @Override
  public SiemPublishResult publish(List<SiemEventPayload> events) {
    try {
      StringBuilder ndjson = new StringBuilder();
      for (SiemEventPayload event : events) {
        ndjson.append(objectMapper.writeValueAsString(event)).append('\n');
      }
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(properties.endpointUrl()))
              .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
              .header(HttpHeaders.CONTENT_TYPE, "application/x-ndjson");
      applyAuth(builder);
      HttpResponse<String> response =
          httpClient.send(builder.POST(HttpRequest.BodyPublishers.ofString(ndjson.toString())).build(), HttpResponse.BodyHandlers.ofString());
      int code = response.statusCode();
      if (code >= 200 && code < 300) {
        return SiemPublishResult.ok();
      }
      if (code == 429 || code >= 500) {
        return SiemPublishResult.retryableFailure("SIEM HTTP " + code);
      }
      return SiemPublishResult.nonRetryableFailure("SIEM HTTP " + code);
    } catch (Exception ex) {
      return SiemPublishResult.retryableFailure("SIEM publish failed");
    }
  }

  private void applyAuth(HttpRequest.Builder builder) {
    String mode = properties.effectiveAuthMode();
    if ("bearer".equals(mode)) {
      builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.bearerToken());
      return;
    }
    if ("header".equals(mode)
        && properties.customHeaderName() != null
        && !properties.customHeaderName().isBlank()) {
      builder.header(properties.customHeaderName(), properties.customHeaderValue() == null ? "" : properties.customHeaderValue());
    }
  }
}
