package com.notebook.lumen.search.provider;

import com.notebook.lumen.search.shared.exception.SearchException;
import org.springframework.http.HttpStatus;

public class SearchProviderException extends SearchException {
  public SearchProviderException(String errorCode, String message) {
    super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message);
  }

  public SearchProviderException(String errorCode, String message, RuntimeException cause) {
    super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message);
    initCause(cause);
  }
}
