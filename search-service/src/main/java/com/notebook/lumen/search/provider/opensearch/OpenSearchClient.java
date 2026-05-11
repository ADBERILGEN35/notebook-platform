package com.notebook.lumen.search.provider.opensearch;

import com.notebook.lumen.search.provider.SearchProviderException;
import com.notebook.lumen.search.shared.config.SearchProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchClient {
  private final SearchProperties properties;
  private final HttpClient httpClient;

  public OpenSearchClient(SearchProperties properties) {
    this.properties = properties;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.opensearch().effectiveConnectTimeoutMs()))
            .build();
  }

  public String get(String path) {
    return send("GET", path, null);
  }

  public String put(String path, String body) {
    return send("PUT", path, body);
  }

  public String post(String path, String body) {
    return send("POST", path, body);
  }

  private String send(String method, String path, String body) {
    if (!properties.opensearch().configured()) {
      throw new SearchProviderException(
          "SEARCH_PROVIDER_MISCONFIGURED", "OpenSearch URL is not configured");
    }
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(uri(path))
              .timeout(Duration.ofMillis(properties.opensearch().effectiveSocketTimeoutMs()))
              .header("Content-Type", "application/json");
      if (hasText(properties.opensearch().username())) {
        String credentials =
            properties.opensearch().username()
                + ":"
                + nullToEmpty(properties.opensearch().password());
        builder.header(
            "Authorization",
            "Basic "
                + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
      }
      HttpRequest request =
          body == null
              ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
              : builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return response.body();
      }
      throw new SearchProviderException(
          response.statusCode() == 408 || response.statusCode() == 504
              ? "SEARCH_PROVIDER_TIMEOUT"
              : "OPENSEARCH_UNAVAILABLE",
          "OpenSearch request failed with status " + response.statusCode());
    } catch (SearchProviderException e) {
      throw e;
    } catch (java.net.http.HttpTimeoutException e) {
      throw new SearchProviderException(
          "SEARCH_PROVIDER_TIMEOUT", "OpenSearch request timed out", new RuntimeException(e));
    } catch (RuntimeException e) {
      throw new SearchProviderException("OPENSEARCH_UNAVAILABLE", "OpenSearch request failed", e);
    } catch (Exception e) {
      throw new SearchProviderException(
          "OPENSEARCH_UNAVAILABLE", "OpenSearch request failed", new RuntimeException(e));
    }
  }

  private URI uri(String path) {
    String base = properties.opensearch().url();
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    return URI.create(normalizedBase + normalizedPath);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
