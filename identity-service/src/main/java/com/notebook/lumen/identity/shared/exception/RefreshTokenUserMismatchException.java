package com.notebook.lumen.identity.shared.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenUserMismatchException extends IdentityRuntimeException {
  public RefreshTokenUserMismatchException() {
    super(
        "REFRESH_TOKEN_USER_MISMATCH",
        HttpStatus.FORBIDDEN,
        "Refresh token does not belong to authenticated user");
  }
}
