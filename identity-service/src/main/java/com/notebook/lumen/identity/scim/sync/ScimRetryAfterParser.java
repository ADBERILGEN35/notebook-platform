package com.notebook.lumen.identity.scim.sync;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/** Parses Retry-After header values without exposing raw header in API output (Faz 116). */
public final class ScimRetryAfterParser {

  static final String WARNING_RETRY_AFTER_CAPPED = "SCIM_DELTA_RETRY_AFTER_CAPPED";

  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US);

  private ScimRetryAfterParser() {}

  public record ParseResult(
      int retryAfterSeconds,
      boolean retryAfterObserved,
      boolean retryAfterCapped,
      boolean invalidFallback,
      List<String> warnings) {}

  public static ParseResult parse(
      String retryAfterHeader, int maxRetryAfterSeconds, int fallbackSeconds) {
    List<String> warnings = new ArrayList<>();
    int safeMax = Math.max(1, maxRetryAfterSeconds);
    int safeFallback = Math.max(1, fallbackSeconds);

    if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
      return new ParseResult(safeFallback, false, false, true, warnings);
    }

    OptionalInt seconds = parseSeconds(retryAfterHeader.trim());
    if (seconds.isEmpty()) {
      warnings.add(ScimDeltaStrategyResolver.WARNING_RETRY_AFTER_OBSERVED);
      return new ParseResult(safeFallback, false, false, true, warnings);
    }

    int value = seconds.getAsInt();
    if (value < 0) {
      return new ParseResult(safeFallback, false, false, true, warnings);
    }

    warnings.add(ScimDeltaStrategyResolver.WARNING_RETRY_AFTER_OBSERVED);
    boolean capped = value > safeMax;
    if (capped) {
      warnings.add(WARNING_RETRY_AFTER_CAPPED);
      value = safeMax;
    }
    return new ParseResult(value, true, capped, false, warnings);
  }

  private static OptionalInt parseSeconds(String value) {
    try {
      long seconds = Long.parseLong(value);
      if (seconds > Integer.MAX_VALUE) {
        return OptionalInt.of(Integer.MAX_VALUE);
      }
      return OptionalInt.of((int) seconds);
    } catch (NumberFormatException ignored) {
      // HTTP-date
    }
    try {
      ZonedDateTime date = ZonedDateTime.parse(value, HTTP_DATE);
      long delta = date.toInstant().getEpochSecond() - Instant.now().getEpochSecond();
      if (delta < 0) {
        return OptionalInt.of(0);
      }
      if (delta > Integer.MAX_VALUE) {
        return OptionalInt.of(Integer.MAX_VALUE);
      }
      return OptionalInt.of((int) delta);
    } catch (DateTimeParseException ignored) {
      return OptionalInt.empty();
    }
  }

  public static Instant nextRecommendedAttempt(Instant from, int retryAfterSeconds) {
    return from.plusSeconds(Math.max(0, retryAfterSeconds));
  }
}
