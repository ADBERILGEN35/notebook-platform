package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends IdentityRuntimeException {

  public EmailAlreadyExistsException(String email) {
    super("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "Email already exists: " + email);
  }
}
