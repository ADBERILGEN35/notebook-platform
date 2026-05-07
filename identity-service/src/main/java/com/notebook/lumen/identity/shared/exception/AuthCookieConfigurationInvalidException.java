package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class AuthCookieConfigurationInvalidException extends IdentityRuntimeException {
  public AuthCookieConfigurationInvalidException(String message) {
    super("AUTH_COOKIE_CONFIGURATION_INVALID", HttpStatus.INTERNAL_SERVER_ERROR, message);
  }
}
