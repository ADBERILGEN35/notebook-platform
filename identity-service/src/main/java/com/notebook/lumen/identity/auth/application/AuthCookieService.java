package com.notebook.lumen.identity.auth.application;

import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.shared.config.AuthTransportProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {
  private final AuthTransportProperties authTransportProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthCookieService(AuthTransportProperties authTransportProperties) {
    this.authTransportProperties = authTransportProperties;
  }

  public void writeAuthCookies(
      HttpServletResponse response, HttpServletRequest request, AuthResponse authResponse) {
    writeTokenCookie(
        response,
        authTransportProperties.effectiveAccessCookieName(),
        authResponse.accessToken(),
        Math.toIntExact(authResponse.expiresIn()),
        true);
    writeTokenCookie(
        response,
        authTransportProperties.effectiveRefreshCookieName(),
        authResponse.refreshToken(),
        -1,
        true);
    writeTokenCookie(
        response,
        authTransportProperties.effectiveCsrfCookieName(),
        generateCsrfToken(),
        -1,
        false);
  }

  public void clearAuthCookies(HttpServletResponse response) {
    writeTokenCookie(response, authTransportProperties.effectiveAccessCookieName(), "", 0, true);
    writeTokenCookie(response, authTransportProperties.effectiveRefreshCookieName(), "", 0, true);
    writeTokenCookie(response, authTransportProperties.effectiveCsrfCookieName(), "", 0, false);
  }

  public String readRefreshTokenCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    String refreshCookieName = authTransportProperties.effectiveRefreshCookieName();
    for (Cookie cookie : cookies) {
      if (refreshCookieName.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private void writeTokenCookie(
      HttpServletResponse response, String name, String value, int maxAge, boolean httpOnly) {
    Cookie cookie = new Cookie(name, value == null ? "" : value);
    cookie.setHttpOnly(httpOnly);
    cookie.setSecure(authTransportProperties.cookieSecure());
    cookie.setPath(authTransportProperties.effectiveCookiePath());
    cookie.setMaxAge(maxAge);
    if (authTransportProperties.cookieDomain() != null
        && !authTransportProperties.cookieDomain().isBlank()) {
      cookie.setDomain(authTransportProperties.cookieDomain());
    }
    response.addHeader(
        "Set-Cookie", toSetCookieHeader(cookie, authTransportProperties.effectiveCookieSameSite()));
  }

  private String toSetCookieHeader(Cookie cookie, String sameSite) {
    StringBuilder builder = new StringBuilder();
    builder
        .append(cookie.getName())
        .append("=")
        .append(cookie.getValue())
        .append("; Path=")
        .append(cookie.getPath());
    if (cookie.getDomain() != null && !cookie.getDomain().isBlank()) {
      builder.append("; Domain=").append(cookie.getDomain());
    }
    if (cookie.getMaxAge() >= 0) {
      builder.append("; Max-Age=").append(cookie.getMaxAge());
    }
    if (cookie.getSecure()) {
      builder.append("; Secure");
    }
    if (cookie.isHttpOnly()) {
      builder.append("; HttpOnly");
    }
    builder.append("; SameSite=").append(sameSite);
    return builder.toString();
  }

  private String generateCsrfToken() {
    byte[] bytes = new byte[24];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
