package com.notebook.lumen.identity.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

  @Test
  void normalizesEmail_trimAndLowercase() {
    assertThat(EmailNormalizer.normalize("  TeSt@Example.com  ")).isEqualTo("test@example.com");
  }
}
