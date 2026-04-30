package com.notebook.lumen.content.shared.exception;

import org.springframework.http.HttpStatus;

public class ContentException extends RuntimeException {
  private final HttpStatus status;
  private final String errorCode;

  public ContentException(HttpStatus status, String errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
