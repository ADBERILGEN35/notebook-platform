package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class UserDisabledException extends IdentityRuntimeException {

  public UserDisabledException() {
    super("USER_DISABLED", HttpStatus.FORBIDDEN, "User is disabled");
  }
}
