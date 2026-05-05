package com.notebook.lumen.identity.audit;

import com.notebook.lumen.identity.shared.exception.IdentityRuntimeException;
import org.springframework.http.HttpStatus;

public class AuditAccessException extends IdentityRuntimeException {
  public AuditAccessException(HttpStatus status, String errorCode, String message) {
    super(errorCode, status, message);
  }
}
