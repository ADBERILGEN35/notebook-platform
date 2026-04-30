package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class TokenGenerationException extends IdentityRuntimeException {

  public TokenGenerationException(String message) {
    super("TOKEN_GENERATION_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, message);
  }
}
