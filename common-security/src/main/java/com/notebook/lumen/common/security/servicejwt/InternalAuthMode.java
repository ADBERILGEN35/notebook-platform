package com.notebook.lumen.common.security.servicejwt;

import java.util.Locale;

public enum InternalAuthMode {
  STATIC_TOKEN,
  SERVICE_JWT,
  DUAL;

  public static InternalAuthMode parse(String value) {
    if (value == null || value.isBlank()) {
      return DUAL;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "static-token" -> STATIC_TOKEN;
      case "service-jwt" -> SERVICE_JWT;
      case "dual" -> DUAL;
      default -> throw new IllegalArgumentException("Unsupported INTERNAL_AUTH_MODE: " + value);
    };
  }

  public boolean acceptsStaticToken() {
    return this == STATIC_TOKEN || this == DUAL;
  }

  public boolean acceptsServiceJwt() {
    return this == SERVICE_JWT || this == DUAL;
  }
}
