package com.notebook.lumen.common.security.servicejwt;

public class ServiceJwtValidationException extends RuntimeException {
  private final String errorCode;
  private final boolean insufficientScope;

  public ServiceJwtValidationException(String errorCode, String message) {
    this(errorCode, message, false);
  }

  public ServiceJwtValidationException(
      String errorCode, String message, boolean insufficientScope) {
    super(message);
    this.errorCode = errorCode;
    this.insufficientScope = insufficientScope;
  }

  public String errorCode() {
    return errorCode;
  }

  public boolean insufficientScope() {
    return insufficientScope;
  }
}
