package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class MfaException extends IdentityRuntimeException {
  public MfaException(String errorCode, HttpStatus status, String message) {
    super(errorCode, status, message);
  }
}
