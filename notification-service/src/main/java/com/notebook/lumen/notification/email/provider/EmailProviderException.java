package com.notebook.lumen.notification.email.provider;

public class EmailProviderException extends RuntimeException {
  public EmailProviderException(String message, Throwable cause) {
    super(message, cause);
  }

  public EmailProviderException(String message) {
    super(message);
  }
}
