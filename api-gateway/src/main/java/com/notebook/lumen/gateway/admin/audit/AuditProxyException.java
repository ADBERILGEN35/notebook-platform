package com.notebook.lumen.gateway.admin.audit;

import com.notebook.lumen.gateway.error.ErrorCode;
import org.springframework.http.HttpStatus;

class AuditProxyException extends RuntimeException {
  private final HttpStatus status;
  private final ErrorCode errorCode;

  AuditProxyException(HttpStatus status, ErrorCode errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  HttpStatus status() {
    return status;
  }

  ErrorCode errorCode() {
    return errorCode;
  }
}
