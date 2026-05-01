package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenTypeException extends IdentityRuntimeException {
  public InvalidTokenTypeException() {
    super("INVALID_TOKEN_TYPE", HttpStatus.UNAUTHORIZED, "Invalid token type");
  }
}
