package com.notebook.lumen.workspace.shared.exception;

import org.springframework.http.HttpStatus;

public class MissingUserContextException extends WorkspaceRuntimeException {
  public MissingUserContextException(String message) {
    super(HttpStatus.UNAUTHORIZED, "MISSING_USER_CONTEXT", message);
  }
}
