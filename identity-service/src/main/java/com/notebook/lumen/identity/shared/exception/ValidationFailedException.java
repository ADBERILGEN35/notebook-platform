package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class ValidationFailedException extends IdentityRuntimeException {

  public ValidationFailedException(String message) {
    super("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, message);
  }
}
