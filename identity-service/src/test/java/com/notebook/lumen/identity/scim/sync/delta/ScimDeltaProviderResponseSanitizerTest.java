package com.notebook.lumen.identity.scim.sync.delta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScimDeltaProviderResponseSanitizerTest {

  @Test
  void extractsAggregateCountWithoutRetainingPayload() {
    String body =
        """
        {"schemas":["urn:ietf:params:scim:api:messages:2.0:ListResponse"],"totalResults":2,\
        "Resources":[{},{}]}
        """;
    var sanitized = ScimDeltaProviderResponseSanitizer.sanitize(body);
    assertThat(sanitized.fetchedResourceCount()).isEqualTo(2);
    assertThat(sanitized.validListResponseShape()).isTrue();
    assertThat(sanitized.warnings())
        .contains(ScimDeltaRemoteFetchWarnings.PROVIDER_RESPONSE_SANITIZED);
  }

  @Test
  void detectsNextCursorWithoutReturningBody() {
    String body =
        """
        {"totalResults":1,"Resources":[{}],"nextCursor":"opaque-cursor-token"}
        """;
    var sanitized = ScimDeltaProviderResponseSanitizer.sanitize(body);
    assertThat(sanitized.nextCursorPresent()).isTrue();
    assertThat(sanitized.warnings()).contains(ScimDeltaRemoteFetchWarnings.NEXT_CURSOR_PRESENT);
  }
}
