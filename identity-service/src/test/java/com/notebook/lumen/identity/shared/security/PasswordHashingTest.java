package com.notebook.lumen.identity.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class PasswordHashingTest {

  @Test
  void argon2id_encodeAndVerify() {
    // Keep it lightweight for unit tests.
    Argon2PasswordEncoder encoder =
        new Argon2PasswordEncoder(
            8, // saltLength
            16, // hashLength
            1, // parallelism
            4096, // memory (KiB)
            1 // iterations
            );

    String password = "Abcdefg1hij";
    String encoded = encoder.encode(password);

    assertThat(encoded).isNotBlank();
    assertThat(encoder.matches(password, encoded)).isTrue();
    assertThat(encoder.matches("WrongPassword1", encoded)).isFalse();
  }
}
