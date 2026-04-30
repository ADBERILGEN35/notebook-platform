package com.notebook.lumen.identity.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends IdentityRuntimeException {

  public UserNotFoundException(UUID userId) {
    super("USER_NOT_FOUND", HttpStatus.UNAUTHORIZED, "User not found");
  }
}
