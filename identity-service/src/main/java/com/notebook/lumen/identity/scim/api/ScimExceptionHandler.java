package com.notebook.lumen.identity.scim.api;

import com.notebook.lumen.identity.scim.application.ScimException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ScimController.class)
public class ScimExceptionHandler {

  @ExceptionHandler(ScimException.class)
  public ResponseEntity<ScimErrorResponse> handleScim(ScimException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(ScimErrorResponse.of(ex.getStatus().value(), ex.getScimType(), ex.getMessage()));
  }
}
