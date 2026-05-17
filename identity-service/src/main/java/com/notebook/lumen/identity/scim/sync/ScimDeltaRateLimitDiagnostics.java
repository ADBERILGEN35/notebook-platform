package com.notebook.lumen.identity.scim.sync;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderFetchResult;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaStoppedReason;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Sanitized rate-limit / remote-fetch diagnostics for delta POC (Faz 116–119). */
public record ScimDeltaRateLimitDiagnostics(
    boolean remoteFetchEnabled,
    boolean remoteFetchConfigured,
    boolean remoteFetchAttempted,
    boolean remoteMultiPageEnabled,
    boolean rateLimitAware,
    boolean retryAfterObserved,
    Integer retryAfterSeconds,
    boolean retryAfterCapped,
    Instant nextRecommendedAttemptAt,
    ScimProviderErrorClass providerErrorClass,
    int backoffBaseSeconds,
    int httpTimeoutMs,
    int fetchedResourceCount,
    int pagesObserved,
    boolean nextCursorPresent,
    String stoppedReason,
    boolean pageLimitReached,
    boolean resourceLimitReached,
    List<String> warnings) {

  public int pageObserved() {
    return pagesObserved;
  }

  public static ScimDeltaRateLimitDiagnostics empty(
      ScimProperties properties, List<String> baseWarnings, ScimDeltaStoppedReason stoppedReason) {
    return new ScimDeltaRateLimitDiagnostics(
        properties.deltaRemoteFetchEnabled(),
        properties.deltaRemoteFetchConfigured(),
        false,
        properties.deltaRemoteMultiPageEnabled(),
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
        stoppedReason.name(),
        false,
        false,
        baseWarnings);
  }

  public static ScimDeltaRateLimitDiagnostics empty(
      ScimProperties properties, List<String> baseWarnings) {
    return empty(properties, baseWarnings, ScimDeltaStoppedReason.NOT_CONFIGURED);
  }

  public static ScimDeltaRateLimitDiagnostics fromRemoteFetch(
      ScimProperties properties, List<String> baseWarnings, ScimDeltaProviderFetchResult fetch) {
    return fromMultiPage(
        properties,
        baseWarnings,
        fetch,
        fetch.pageObserved(),
        fetch.fetchedResourceCount(),
        fetch.nextCursorPresent(),
        fetch.nextCursorPresent()
            ? ScimDeltaStoppedReason.NO_NEXT_CURSOR
            : ScimDeltaStoppedReason.SINGLE_PAGE_ONLY,
        false,
        false);
  }

  public static ScimDeltaRateLimitDiagnostics fromMultiPage(
      ScimProperties properties,
      List<String> baseWarnings,
      ScimDeltaProviderFetchResult lastPage,
      int pagesObserved,
      int totalResources,
      boolean nextCursorPresent,
      ScimDeltaStoppedReason stoppedReason,
      boolean pageLimitReached,
      boolean resourceLimitReached) {
    Set<String> warnings = new LinkedHashSet<>(baseWarnings);
    if (lastPage != null) {
      warnings.addAll(lastPage.warnings());
    }
    return new ScimDeltaRateLimitDiagnostics(
        properties.deltaRemoteFetchEnabled(),
        properties.deltaRemoteFetchConfigured(),
        lastPage != null && lastPage.attempted(),
        properties.deltaRemoteMultiPageEnabled(),
        properties.providerRateLimitAware(),
        lastPage != null && lastPage.retryAfterObserved(),
        lastPage == null ? null : lastPage.retryAfterSeconds(),
        lastPage != null && lastPage.retryAfterCapped(),
        lastPage == null ? null : lastPage.nextRecommendedAttemptAt(),
        lastPage == null ? ScimProviderErrorClass.NONE : lastPage.providerErrorClass(),
        properties.deltaBackoffBaseSeconds(),
        properties.deltaHttpTimeoutMs(),
        totalResources,
        pagesObserved,
        nextCursorPresent,
        stoppedReason.name(),
        pageLimitReached,
        resourceLimitReached,
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
        properties.deltaRemoteMultiPageEnabled(),
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
        ScimDeltaStoppedReason.SINGLE_PAGE_ONLY.name(),
        false,
        false,
        warnings);
  }
}
