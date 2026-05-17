package com.notebook.lumen.identity.scim.sync.delta;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimDeltaProviderKind;
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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JDK HttpClient-based read-only provider fetch (Faz 117–119). Discards raw body after
 * sanitization; never logs token or Authorization header.
 */
@Component
public class DefaultScimDeltaProviderClient implements ScimDeltaProviderClient {

  private final ScimProperties properties;
  private final HttpClient httpClient;

  @Autowired
  public DefaultScimDeltaProviderClient(ScimProperties properties) {
    this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  /** Test-only; not a Spring bean constructor. */
  static DefaultScimDeltaProviderClient forTest(ScimProperties properties, HttpClient httpClient) {
    return new DefaultScimDeltaProviderClient(properties, httpClient);
  }

  private DefaultScimDeltaProviderClient(ScimProperties properties, HttpClient httpClient) {
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
    ScimDeltaProviderKind kind = ScimDeltaProviderKind.fromConfig(properties.providerType());

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
            1,
            false,
            false,
            retryAfter.retryAfterObserved(),
            retryAfter.retryAfterObserved() ? retryAfter.retryAfterSeconds() : null,
            retryAfter.retryAfterCapped(),
            classification.retryable()
                ? ScimRetryAfterParser.nextRecommendedAttempt(now, retryAfter.retryAfterSeconds())
                : null,
            classification.errorClass(),
            warnings,
            Optional.empty());
      }

      var sanitized = ScimDeltaProviderResponseSanitizer.sanitize(response.body(), kind);
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
            warnings,
            Optional.empty());
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
          warnings,
          sanitized.continuation());

    } catch (java.net.http.HttpTimeoutException ex) {
      var timeout = ScimProviderResponseClassifier.classifyTimeout();
      warnings.addAll(timeout.warnings());
      return new ScimDeltaProviderFetchResult(
          true,
          false,
          0,
          0,
          1,
          false,
          false,
          false,
          null,
          false,
          ScimRetryAfterParser.nextRecommendedAttempt(now, backoffBase),
          timeout.errorClass(),
          warnings,
          Optional.empty());
    } catch (Exception ex) {
      var unavailable = ScimProviderResponseClassifier.classifyHttpStatus(503);
      warnings.addAll(unavailable.warnings());
      return new ScimDeltaProviderFetchResult(
          true,
          false,
          0,
          0,
          1,
          false,
          false,
          false,
          null,
          false,
          ScimRetryAfterParser.nextRecommendedAttempt(now, backoffBase),
          unavailable.errorClass(),
          warnings,
          Optional.empty());
    }
  }
}
