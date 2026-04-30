package com.notebook.lumen.common.security.secrets;

import java.util.Objects;

public final class SecretValue {
  private static final String MASK = "****";

  private final String name;
  private final String value;

  private SecretValue(String name, String value) {
    this.name = Objects.requireNonNull(name, "name");
    this.value = value;
  }

  public static SecretValue of(String name, String value) {
    return new SecretValue(name, value);
  }

  public String value() {
    return value;
  }

  public boolean hasText() {
    return value != null && !value.isBlank();
  }

  public SecretValue requireNonBlank() {
    if (!hasText()) {
      throw new SecretResolutionException("Required secret is blank: " + name);
    }
    return this;
  }

  public String masked() {
    return MASK;
  }

  @Override
  public String toString() {
    return "SecretValue{name='" + name + "', value='" + MASK + "'}";
  }
}
