package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.auth")
public record GatewayAuthProperties(
    String tokenTransport,
    String accessCookieName,
    String csrfCookieName,
    String csrfHeaderName) {
  public String effectiveTransport() {
    if (tokenTransport == null || tokenTransport.isBlank()) {
      return "bearer";
    }
    return tokenTransport.trim().toLowerCase();
  }

  public boolean cookieTransportEnabled() {
    String transport = effectiveTransport();
    return "cookie".equals(transport) || "dual".equals(transport);
  }

  public String effectiveAccessCookieName() {
    return (accessCookieName == null || accessCookieName.isBlank())
        ? "__Host-np_access"
        : accessCookieName;
  }

  public String effectiveCsrfCookieName() {
    return (csrfCookieName == null || csrfCookieName.isBlank()) ? "NP-XSRF-TOKEN" : csrfCookieName;
  }

  public String effectiveCsrfHeaderName() {
    return (csrfHeaderName == null || csrfHeaderName.isBlank()) ? "X-CSRF-Token" : csrfHeaderName;
  }
}
