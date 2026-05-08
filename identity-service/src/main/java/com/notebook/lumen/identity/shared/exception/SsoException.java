package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class SsoException extends IdentityRuntimeException {
  public SsoException(String errorCode, HttpStatus status, String message) {
    super(errorCode, status, message);
  }
}
