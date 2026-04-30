package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends IdentityRuntimeException {

  public InvalidRefreshTokenException() {
    super("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid refresh token");
  }
}
