package com.notebook.lumen.identity.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthTransportProperties(
    String tokenTransport,
    boolean cookieSecure,
    String cookieSameSite,
    String cookieDomain,
    String cookiePath,
    String accessCookieName,
    String refreshCookieName,
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

  public boolean bearerTransportEnabled() {
    String transport = effectiveTransport();
    return "bearer".equals(transport) || "dual".equals(transport);
  }

  public String effectiveCookiePath() {
    return (cookiePath == null || cookiePath.isBlank()) ? "/" : cookiePath;
  }

  public String effectiveAccessCookieName() {
    return (accessCookieName == null || accessCookieName.isBlank())
        ? "__Host-np_access"
        : accessCookieName;
  }

  public String effectiveRefreshCookieName() {
    return (refreshCookieName == null || refreshCookieName.isBlank())
        ? "__Host-np_refresh"
        : refreshCookieName;
  }

  public String effectiveCsrfCookieName() {
    return (csrfCookieName == null || csrfCookieName.isBlank()) ? "NP-XSRF-TOKEN" : csrfCookieName;
  }

  public String effectiveCsrfHeaderName() {
    return (csrfHeaderName == null || csrfHeaderName.isBlank()) ? "X-CSRF-Token" : csrfHeaderName;
  }

  public String effectiveCookieSameSite() {
    return (cookieSameSite == null || cookieSameSite.isBlank()) ? "Lax" : cookieSameSite;
  }
}
