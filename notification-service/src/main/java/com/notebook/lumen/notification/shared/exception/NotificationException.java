package com.notebook.lumen.notification.shared.exception;

import org.springframework.http.HttpStatus;

public class NotificationException extends RuntimeException {
  private final HttpStatus status;
  private final String errorCode;

  public NotificationException(HttpStatus status, String errorCode, String message) {
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
