package com.notebook.lumen.identity.scim.sync.delta;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimProviderErrorClass;
import com.notebook.lumen.identity.scim.sync.ScimProviderResponseClassifier;
import com.notebook.lumen.identity.scim.sync.ScimRetryAfterParser;
import com.notebook.lumen.identity.scim.sync.ScimRetryAfterParser.ParseResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * JDK HttpClient-based read-only provider fetch (Faz 117). Discards raw body after sanitization; never
 * logs token or Authorization header.
 */
@Component
public class DefaultScimDeltaProviderClient implements ScimDeltaProviderClient {

  private final ScimProperties properties;
  private final HttpClient httpClient;

  public DefaultScimDeltaProviderClient(ScimProperties properties) {
    this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  DefaultScimDeltaProviderClient(ScimProperties properties, HttpClient httpClient) {
    this.properties = properties;
    this.httpClient = httpClient;
  }

  @Override
  public ScimDeltaProviderFetchResult fetch(ScimDeltaProviderRequest request, String bearerToken) {
    if (request.method() != ScimDeltaHttpMethod.GET) {
      throw new IllegalArgumentException("Only GET is permitted");
    }
    if (bearerToken == null || bearerToken.isBlank()) {
      throw new IllegalArgumentException("Bearer token required for remote fetch");
    }

    List<String> warnings = new ArrayList<>();
    warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_ATTEMPTED);
    warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_SUPPRESSED);

    Instant now = Instant.now();
    int backoffBase = Math.max(1, properties.deltaBackoffBaseSeconds());

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(request.requestUri()))
              .timeout(Duration.ofMillis(Math.max(500, properties.deltaHttpTimeoutMs())))
              .GET()
              .header("Accept", "application/scim+json")
              .header("Authorization", "Bearer " + bearerToken)
              .build();

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      String retryAfterHeader = response.headers().firstValue("Retry-After").orElse(null);
      ParseResult retryAfter =
          ScimRetryAfterParser.parse(
              retryAfterHeader, properties.deltaMaxRetryAfterSeconds(), backoffBase);
      warnings.addAll(retryAfter.warnings());

      var classification = ScimProviderResponseClassifier.classifyHttpStatus(response.statusCode());
      warnings.addAll(classification.warnings());

      if (classification.errorClass() != ScimProviderErrorClass.NONE) {
        return new ScimDeltaProviderFetchResult(
            true,
            true,
            response.statusCode(),
            0,
            0,
            false,
            false,
            retryAfter.retryAfterObserved(),
            retryAfter.retryAfterObserved() ? retryAfter.retryAfterSeconds() : null,
            retryAfter.retryAfterCapped(),
            classification.retryable()
                ? ScimRetryAfterParser.nextRecommendedAttempt(now, retryAfter.retryAfterSeconds())
                : null,
            classification.errorClass(),
            warnings);
      }

      var sanitized = ScimDeltaProviderResponseSanitizer.sanitize(response.body());
      warnings.addAll(sanitized.warnings());

      if (!sanitized.validListResponseShape()) {
        warnings.addAll(ScimProviderResponseClassifier.classifyBadResponse().warnings());
        return new ScimDeltaProviderFetchResult(
            true,
            true,
            response.statusCode(),
            0,
            1,
            sanitized.nextCursorPresent(),
            false,
            retryAfter.retryAfterObserved(),
            retryAfter.retryAfterObserved() ? retryAfter.retryAfterSeconds() : null,
            retryAfter.retryAfterCapped(),
            null,
            ScimProviderErrorClass.PROVIDER_BAD_RESPONSE,
            warnings);
      }

      return new ScimDeltaProviderFetchResult(
          true,
          true,
          response.statusCode(),
          sanitized.fetchedResourceCount(),
          1,
          sanitized.nextCursorPresent(),
          true,
          retryAfter.retryAfterObserved(),
          retryAfter.retryAfterObserved() ? retryAfter.retryAfterSeconds() : null,
          retryAfter.retryAfterCapped(),
          null,
          ScimProviderErrorClass.NONE,
          warnings);

    } catch (java.net.http.HttpTimeoutException ex) {
      var timeout = ScimProviderResponseClassifier.classifyTimeout();
      warnings.addAll(timeout.warnings());
      return new ScimDeltaProviderFetchResult(
          true,
          false,
          0,
          0,
          0,
          false,
          false,
          false,
          null,
          false,
          ScimRetryAfterParser.nextRecommendedAttempt(now, backoffBase),
          timeout.errorClass(),
          warnings);
    } catch (Exception ex) {
      var unavailable = ScimProviderResponseClassifier.classifyHttpStatus(503);
      warnings.addAll(unavailable.warnings());
      return new ScimDeltaProviderFetchResult(
          true,
          false,
          0,
          0,
          0,
          false,
          false,
          false,
          null,
          false,
          ScimRetryAfterParser.nextRecommendedAttempt(now, backoffBase),
          unavailable.errorClass(),
          warnings);
    }
  }
}
