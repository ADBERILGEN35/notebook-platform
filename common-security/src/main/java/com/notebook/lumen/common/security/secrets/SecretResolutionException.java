package com.notebook.lumen.common.security.secrets;

public class SecretResolutionException extends RuntimeException {
  public SecretResolutionException(String message) {
    super(message);
  }

  public SecretResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
