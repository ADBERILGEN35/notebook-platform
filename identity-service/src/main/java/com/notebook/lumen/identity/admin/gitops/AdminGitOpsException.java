package com.notebook.lumen.identity.admin.gitops;

import com.notebook.lumen.identity.shared.exception.IdentityRuntimeException;
import org.springframework.http.HttpStatus;

public final class AdminGitOpsException extends IdentityRuntimeException {
  public AdminGitOpsException(String errorCode, HttpStatus status, String message) {
    super(errorCode, status, message);
  }
}
