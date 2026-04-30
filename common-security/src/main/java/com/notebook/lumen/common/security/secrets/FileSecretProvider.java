package com.notebook.lumen.common.security.secrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class FileSecretProvider implements SecretProvider {
  @Override
  public Optional<SecretValue> resolve(String path) {
    if (path == null || path.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          SecretValue.of(path, Files.readString(Path.of(path), StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new SecretResolutionException("Failed to read secret file: " + path, e);
    }
  }
}
