package com.notebook.lumen.identity.scim.sync.delta;

import com.notebook.lumen.identity.scim.sync.ScimProviderErrorClass;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Sanitized aggregate result of a read-only provider fetch (Faz 117). No raw body or token. */
public record ScimDeltaProviderFetchResult(
    boolean attempted,
    boolean httpCallCompleted,
    int httpStatus,
    int fetchedResourceCount,
    int pageObserved,
    boolean nextCursorPresent,
    boolean validListResponseShape,
    boolean retryAfterObserved,
    Integer retryAfterSeconds,
    boolean retryAfterCapped,
    Instant nextRecommendedAttemptAt,
    ScimProviderErrorClass providerErrorClass,
    List<String> warnings,
    Optional<ScimDeltaPaginationContinuation> paginationContinuation) {

  public static ScimDeltaProviderFetchResult notAttempted(List<String> warnings) {
    return new ScimDeltaProviderFetchResult(
        false,
        false,
        0,
        0,
        0,
        false,
        false,
        false,
        null,
        false,
        null,
        ScimProviderErrorClass.NONE,
        warnings,
        Optional.empty());
  }
}
