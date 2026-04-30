package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends IdentityRuntimeException {

  public InvalidCredentialsException() {
    super("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password");
  }
}
