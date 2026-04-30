package com.notebook.lumen.common.security.sanitization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTest {
  @Test
  void sensitiveMetadataValuesAreMasked() {
    Map<String, Object> sanitized =
        SensitiveDataSanitizer.sanitizeMetadata(
            Map.of(
                "authorization", "Bearer token",
                "safe", "value",
                "nested", Map.of("privateKey", "pem-value")));

    assertThat(sanitized).containsEntry("authorization", "****").containsEntry("safe", "value");
    assertThat(((Map<?, ?>) sanitized.get("nested")).get("privateKey")).isEqualTo("****");
  }

  @Test
  void sensitiveValidationMessageIsGeneric() {
    assertThat(SensitiveDataSanitizer.validationMessageFor("password", "must not be leaked"))
        .isEqualTo("Invalid sensitive value");
  }
}
