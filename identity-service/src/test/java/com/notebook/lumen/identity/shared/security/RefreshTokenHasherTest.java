package com.notebook.lumen.identity.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenHasherTest {

  @Test
  void hash_isDeterministicAndLooksLikeSha256Hex() {
    String token = "some-refresh-token-plaintext";
    String h1 = RefreshTokenHasher.hash(token);
    String h2 = RefreshTokenHasher.hash(token);

    assertThat(h1).isEqualTo(h2);
    assertThat(h1).hasSize(64);
    assertThat(h1).matches("^[0-9a-f]+$");
  }
}
