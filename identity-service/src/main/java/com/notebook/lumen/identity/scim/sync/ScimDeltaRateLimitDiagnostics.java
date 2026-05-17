package com.notebook.lumen.identity.scim.sync;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderFetchResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Sanitized rate-limit / remote-fetch diagnostics for delta POC (Faz 116–117). */
public record ScimDeltaRateLimitDiagnostics(
    boolean remoteFetchEnabled,
    boolean remoteFetchConfigured,
    boolean remoteFetchAttempted,
    boolean rateLimitAware,
    boolean retryAfterObserved,
    Integer retryAfterSeconds,
    boolean retryAfterCapped,
    Instant nextRecommendedAttemptAt,
    ScimProviderErrorClass providerErrorClass,
    int backoffBaseSeconds,
    int httpTimeoutMs,
    int fetchedResourceCount,
    int pageObserved,
    boolean nextCursorPresent,
    List<String> warnings) {

  public static ScimDeltaRateLimitDiagnostics empty(ScimProperties properties, List<String> baseWarnings) {
    return new ScimDeltaRateLimitDiagnostics(
        properties.deltaRemoteFetchEnabled(),
        properties.deltaRemoteFetchConfigured(),
        false,
        properties.providerRateLimitAware(),
        false,
        null,
        false,
        null,
        ScimProviderErrorClass.NONE,
        properties.deltaBackoffBaseSeconds(),
        properties.deltaHttpTimeoutMs(),
        0,
        0,
        false,
        baseWarnings);
  }

  public static ScimDeltaRateLimitDiagnostics fromRemoteFetch(
      ScimProperties properties, List<String> baseWarnings, ScimDeltaProviderFetchResult fetch) {
    Set<String> warnings = new LinkedHashSet<>(baseWarnings);
    warnings.addAll(fetch.warnings());
    return new ScimDeltaRateLimitDiagnostics(
        properties.deltaRemoteFetchEnabled(),
        properties.deltaRemoteFetchConfigured(),
        fetch.attempted(),
        properties.providerRateLimitAware(),
        fetch.retryAfterObserved(),
        fetch.retryAfterSeconds(),
        fetch.retryAfterCapped(),
        fetch.nextRecommendedAttemptAt(),
        fetch.providerErrorClass(),
        properties.deltaBackoffBaseSeconds(),
        properties.deltaHttpTimeoutMs(),
        fetch.fetchedResourceCount(),
        fetch.pageObserved(),
        fetch.nextCursorPresent(),
        List.copyOf(warnings));
  }

  public static ScimDeltaRateLimitDiagnostics simulated(
      ScimProperties properties,
      boolean retryAfterObserved,
      Integer retryAfterSeconds,
      boolean retryAfterCapped,
      Instant nextRecommendedAttemptAt,
      ScimProviderErrorClass providerErrorClass,
      List<String> warnings) {
    return new ScimDeltaRateLimitDiagnostics(
        properties.deltaRemoteFetchEnabled(),
        properties.deltaRemoteFetchConfigured(),
        false,
        properties.providerRateLimitAware(),
        retryAfterObserved,
        retryAfterSeconds,
        retryAfterCapped,
        nextRecommendedAttemptAt,
        providerErrorClass,
        properties.deltaBackoffBaseSeconds(),
        properties.deltaHttpTimeoutMs(),
        0,
        0,
        false,
        warnings);
  }
}
