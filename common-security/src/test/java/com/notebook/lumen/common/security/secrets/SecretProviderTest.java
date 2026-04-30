package com.notebook.lumen.common.security.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretProviderTest {
  @TempDir Path tempDir;

  @Test
  void environmentProviderResolvesSecret() {
    EnvironmentSecretProvider provider =
        new EnvironmentSecretProvider(Map.of("INTERNAL_API_TOKEN_PRIMARY", "secret-token"));

    assertThat(provider.require("INTERNAL_API_TOKEN_PRIMARY").value()).isEqualTo("secret-token");
  }

  @Test
  void fileProviderResolvesSecret() throws Exception {
    Path secretFile = tempDir.resolve("secret.txt");
    Files.writeString(secretFile, "file-secret", StandardCharsets.UTF_8);

    assertThat(new FileSecretProvider().require(secretFile.toString()).value())
        .isEqualTo("file-secret");
  }

  @Test
  void missingRequiredSecretFails() {
    EnvironmentSecretProvider provider = new EnvironmentSecretProvider(Map.of());

    assertThatThrownBy(() -> provider.require("DB_PASSWORD"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("DB_PASSWORD");
  }

  @Test
  void secretValueToStringIsMasked() {
    SecretValue value = SecretValue.of("JWT_PRIVATE_KEY", "super-secret");

    assertThat(value.toString()).contains("****").doesNotContain("super-secret");
  }
}
