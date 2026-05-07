package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class RefreshCookieRequiredException extends IdentityRuntimeException {
  public RefreshCookieRequiredException() {
    super(
        "REFRESH_COOKIE_REQUIRED",
        HttpStatus.UNAUTHORIZED,
        "Refresh token cookie is required for cookie auth transport");
  }
}
