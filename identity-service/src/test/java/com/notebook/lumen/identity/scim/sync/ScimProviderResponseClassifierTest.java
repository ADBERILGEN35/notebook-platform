package com.notebook.lumen.identity.scim.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScimProviderResponseClassifierTest {

  @Test
  void http429IsRateLimited() {
    var c = ScimProviderResponseClassifier.classifyHttpStatus(429);
    assertThat(c.errorClass()).isEqualTo(ScimProviderErrorClass.RATE_LIMITED);
    assertThat(c.warnings()).contains(ScimProviderResponseClassifier.WARNING_RATE_LIMITED);
  }

  @Test
  void http503IsUnavailable() {
    var c = ScimProviderResponseClassifier.classifyHttpStatus(503);
    assertThat(c.errorClass()).isEqualTo(ScimProviderErrorClass.PROVIDER_UNAVAILABLE);
  }

  @Test
  void http401IsAuthFailed() {
    var c = ScimProviderResponseClassifier.classifyHttpStatus(401);
    assertThat(c.errorClass()).isEqualTo(ScimProviderErrorClass.PROVIDER_AUTH_FAILED);
    assertThat(c.warnings()).contains(ScimProviderResponseClassifier.WARNING_PROVIDER_AUTH_FAILED);
  }

  @Test
  void timeoutClassification() {
    var c = ScimProviderResponseClassifier.classifyTimeout();
    assertThat(c.errorClass()).isEqualTo(ScimProviderErrorClass.TIMEOUT);
  }

  @Test
  void badJsonClassification() {
    var c = ScimProviderResponseClassifier.classifyBadResponse();
    assertThat(c.errorClass()).isEqualTo(ScimProviderErrorClass.PROVIDER_BAD_RESPONSE);
  }
}
