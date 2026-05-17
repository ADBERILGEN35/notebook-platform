package com.notebook.lumen.identity.scim.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ScimRetryAfterParserTest {

  @Test
  void parsesSecondsFormat() {
    var result = ScimRetryAfterParser.parse("120", 300, 30);
    assertThat(result.retryAfterSeconds()).isEqualTo(120);
    assertThat(result.retryAfterObserved()).isTrue();
    assertThat(result.retryAfterCapped()).isFalse();
    assertThat(result.warnings()).contains(ScimDeltaStrategyResolver.WARNING_RETRY_AFTER_OBSERVED);
  }

  @Test
  void parsesHttpDateFormat() {
    Instant future = Instant.now().plusSeconds(90);
    String httpDate =
        DateTimeFormatter.RFC_1123_DATE_TIME
            .withLocale(Locale.US)
            .format(future.atZone(ZoneOffset.UTC));
    var result = ScimRetryAfterParser.parse(httpDate, 300, 30);
    assertThat(result.retryAfterObserved()).isTrue();
    assertThat(result.retryAfterSeconds()).isBetween(80, 100);
  }

  @Test
  void invalidRetryAfterUsesFallback() {
    var result = ScimRetryAfterParser.parse("not-a-date-or-seconds", 300, 45);
    assertThat(result.invalidFallback()).isTrue();
    assertThat(result.retryAfterSeconds()).isEqualTo(45);
    assertThat(result.retryAfterObserved()).isFalse();
  }

  @Test
  void capsRetryAfterAboveMax() {
    var result = ScimRetryAfterParser.parse("9999", 300, 30);
    assertThat(result.retryAfterSeconds()).isEqualTo(300);
    assertThat(result.retryAfterCapped()).isTrue();
    assertThat(result.warnings()).contains(ScimRetryAfterParser.WARNING_RETRY_AFTER_CAPPED);
  }
}
