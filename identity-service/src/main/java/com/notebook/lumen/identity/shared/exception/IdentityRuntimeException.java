package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class IdentityRuntimeException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus httpStatus;

  protected IdentityRuntimeException(String errorCode, HttpStatus httpStatus, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
