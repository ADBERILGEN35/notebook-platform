package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class MfaNotEnabledException extends IdentityRuntimeException {
  public MfaNotEnabledException(String message) {
    super("MFA_NOT_ENABLED", HttpStatus.NOT_IMPLEMENTED, message);
  }
}
