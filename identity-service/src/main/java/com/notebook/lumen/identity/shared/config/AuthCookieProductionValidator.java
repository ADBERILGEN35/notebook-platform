package com.notebook.lumen.identity.shared.config;

import com.notebook.lumen.identity.shared.exception.AuthCookieConfigurationInvalidException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieProductionValidator implements ApplicationRunner {
  private final AuthTransportProperties authTransportProperties;
  private final Environment environment;

  public AuthCookieProductionValidator(
      AuthTransportProperties authTransportProperties, Environment environment) {
    this.authTransportProperties = authTransportProperties;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!authTransportProperties.cookieTransportEnabled()) {
      return;
    }
    String accessCookie = authTransportProperties.effectiveAccessCookieName();
    String refreshCookie = authTransportProperties.effectiveRefreshCookieName();
    if ((accessCookie.startsWith("__Host-") || refreshCookie.startsWith("__Host-"))
        && authTransportProperties.cookieDomain() != null
        && !authTransportProperties.cookieDomain().isBlank()) {
      throw new AuthCookieConfigurationInvalidException(
          "__Host- cookies cannot set Domain attribute");
    }

    if ((accessCookie.startsWith("__Host-") || refreshCookie.startsWith("__Host-"))
        && !"/".equals(authTransportProperties.effectiveCookiePath())) {
      throw new AuthCookieConfigurationInvalidException("__Host- cookies require cookie path '/'");
    }

    if (environment.acceptsProfiles(Profiles.of("prod"))
        && !authTransportProperties.cookieSecure()) {
      throw new AuthCookieConfigurationInvalidException(
          "Cookie transport requires AUTH_COOKIE_SECURE=true in prod");
    }
  }
}
