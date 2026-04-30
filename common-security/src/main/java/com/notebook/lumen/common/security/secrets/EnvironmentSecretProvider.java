package com.notebook.lumen.common.security.secrets;

import java.util.Map;
import java.util.Optional;

public class EnvironmentSecretProvider implements SecretProvider {
  private final Map<String, String> environment;

  public EnvironmentSecretProvider() {
    this(System.getenv());
  }

  public EnvironmentSecretProvider(Map<String, String> environment) {
    this.environment = Map.copyOf(environment);
  }

  @Override
  public Optional<SecretValue> resolve(String name) {
    return Optional.ofNullable(environment.get(name)).map(value -> SecretValue.of(name, value));
  }
}
