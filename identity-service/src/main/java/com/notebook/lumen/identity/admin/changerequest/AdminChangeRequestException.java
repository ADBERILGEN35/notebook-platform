package com.notebook.lumen.identity.admin.changerequest;

import com.notebook.lumen.identity.shared.exception.IdentityRuntimeException;
import org.springframework.http.HttpStatus;

public final class AdminChangeRequestException extends IdentityRuntimeException {
  public AdminChangeRequestException(String errorCode, HttpStatus status, String message) {
    super(errorCode, status, message);
  }
}
