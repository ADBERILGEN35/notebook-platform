package com.notebook.lumen.identity.scim.sync;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Encodes/decodes bounded sync-run diagnostic metadata in last_error_summary (Faz 116). */
final class ScimDeltaRunMetadataCodec {

  private static final Pattern RETRY_SECONDS = Pattern.compile("retryAfterSeconds=(\\d+)");
  private static final Pattern RETRY_CAPPED = Pattern.compile("retryAfterCapped=(true|false)");
  private static final Pattern NEXT_ATTEMPT =
      Pattern.compile("nextRecommendedAttemptAt=([^;\\s]+)");

  private ScimDeltaRunMetadataCodec() {}

  static String encode(ScimDeltaRateLimitDiagnostics diagnostics) {
    if (diagnostics == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    if (diagnostics.providerErrorClass() != null
        && diagnostics.providerErrorClass() != ScimProviderErrorClass.NONE) {
      sb.append("providerErrorClass=").append(diagnostics.providerErrorClass().name());
    }
    if (diagnostics.retryAfterSeconds() != null) {
      if (!sb.isEmpty()) sb.append(';');
      sb.append("retryAfterSeconds=").append(diagnostics.retryAfterSeconds());
    }
    if (diagnostics.retryAfterCapped()) {
      if (!sb.isEmpty()) sb.append(';');
      sb.append("retryAfterCapped=true");
    }
    if (diagnostics.nextRecommendedAttemptAt() != null) {
      if (!sb.isEmpty()) sb.append(';');
      sb.append("nextRecommendedAttemptAt=").append(diagnostics.nextRecommendedAttemptAt());
    }
    if (diagnostics.backoffBaseSeconds() > 0) {
      if (!sb.isEmpty()) sb.append(';');
      sb.append("backoffBaseSeconds=").append(diagnostics.backoffBaseSeconds());
    }
    if (diagnostics.remoteFetchAttempted()) {
      if (!sb.isEmpty()) sb.append(';');
      sb.append("remoteFetchAttempted=true");
    }
    if (diagnostics.fetchedResourceCount() > 0) {
      if (!sb.isEmpty()) sb.append(';');
      sb.append("fetchedResourceCount=").append(diagnostics.fetchedResourceCount());
    }
    if (diagnostics.nextCursorPresent()) {
      if (!sb.isEmpty()) sb.append(';');
      sb.append("nextCursorPresent=true");
    }
    String encoded = sb.toString();
    return encoded.isEmpty() ? null : encoded.substring(0, Math.min(512, encoded.length()));
  }

  static Optional<DecodedMetadata> decode(String summary) {
    if (summary == null || summary.isBlank()) {
      return Optional.empty();
    }
    Integer retryAfterSeconds = null;
    boolean retryAfterCapped = false;
    Instant nextAttempt = null;

    Matcher secondsMatcher = RETRY_SECONDS.matcher(summary);
    if (secondsMatcher.find()) {
      retryAfterSeconds = Integer.parseInt(secondsMatcher.group(1));
    }
    Matcher cappedMatcher = RETRY_CAPPED.matcher(summary);
    if (cappedMatcher.find()) {
      retryAfterCapped = Boolean.parseBoolean(cappedMatcher.group(1));
    }
    Matcher nextMatcher = NEXT_ATTEMPT.matcher(summary);
    if (nextMatcher.find()) {
      try {
        nextAttempt = Instant.parse(nextMatcher.group(1));
      } catch (Exception ignored) {
        // ignore malformed timestamp in stored summary
      }
    }
    if (retryAfterSeconds == null && nextAttempt == null && !retryAfterCapped) {
      return Optional.empty();
    }
    return Optional.of(new DecodedMetadata(retryAfterSeconds, retryAfterCapped, nextAttempt));
  }

  record DecodedMetadata(Integer retryAfterSeconds, boolean retryAfterCapped, Instant nextRecommendedAttemptAt) {}
}
