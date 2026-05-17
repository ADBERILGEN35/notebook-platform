package com.notebook.lumen.identity.scim.sync.delta;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.identity.scim.sync.ScimDeltaProviderKind;
import org.junit.jupiter.api.Test;

class ScimDeltaSanitizerContinuationTest {

  @Test
  void nextLinkUsesPathAndQueryOnlyNotFullUrlInToString() {
    String body =
        """
        {"totalResults":1,"Resources":[{}],\
        "@odata.nextLink":"https://idp.example.com/scim/v2/Users?$skiptoken=opaque-token-value"}
        """;
    var sanitized =
        ScimDeltaProviderResponseSanitizer.sanitize(body, ScimDeltaProviderKind.AZURE_AD);
    assertThat(sanitized.nextCursorPresent()).isTrue();
    assertThat(sanitized.continuation()).isPresent();
    assertThat(sanitized.continuation().get().toString()).doesNotContain("opaque-token");
    assertThat(sanitized.continuation().get().toString()).doesNotContain("https://");
  }

  @Test
  void responseToStringDoesNotContainRawCursor() {
    String body =
        """
        {"totalResults":2,"Resources":[{},{}],"nextCursor":"super-secret-cursor"}
        """;
    var sanitized = ScimDeltaProviderResponseSanitizer.sanitize(body);
    assertThat(sanitized.continuation()).isPresent();
    assertThat(sanitized.toString()).doesNotContain("super-secret");
  }
}
