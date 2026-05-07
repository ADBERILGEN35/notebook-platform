package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends IdentityRuntimeException {
  public SessionNotFoundException() {
    super("SESSION_NOT_FOUND", HttpStatus.UNAUTHORIZED, "Active session not found");
  }
}
