package com.notebook.lumen.common.security.secrets;

import java.util.Optional;

public interface SecretProvider {
  Optional<SecretValue> resolve(String name);

  default SecretValue require(String name) {
    return resolve(name)
        .filter(SecretValue::hasText)
        .orElseThrow(() -> new SecretResolutionException("Required secret is missing: " + name));
  }
}
