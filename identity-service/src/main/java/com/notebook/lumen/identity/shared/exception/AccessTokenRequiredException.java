package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class AccessTokenRequiredException extends IdentityRuntimeException {
  public AccessTokenRequiredException() {
    super("ACCESS_TOKEN_REQUIRED", HttpStatus.UNAUTHORIZED, "Access token is required");
  }
}
