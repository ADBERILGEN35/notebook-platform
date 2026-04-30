package com.notebook.lumen.workspace.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class WorkspaceRuntimeException extends RuntimeException {

  private final HttpStatus httpStatus;
  private final String errorCode;

  protected WorkspaceRuntimeException(HttpStatus httpStatus, String errorCode, String message) {
    super(message);
    this.httpStatus = httpStatus;
    this.errorCode = errorCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
