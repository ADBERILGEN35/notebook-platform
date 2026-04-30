package com.notebook.lumen.identity.shared.exception;

import com.notebook.lumen.common.security.sanitization.SensitiveDataSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IdentityRuntimeException.class)
  ResponseEntity<ErrorResponse> handleIdentity(
      IdentityRuntimeException ex, HttpServletRequest request) {
    ErrorResponse body =
        new ErrorResponse(
            Instant.now(),
            ex.getHttpStatus().value(),
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI(),
            requestId(request),
            List.of());
    return ResponseEntity.status(ex.getHttpStatus()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                err ->
                    new ErrorResponse.FieldError(
                        err.getField(),
                        SensitiveDataSanitizer.validationMessageFor(
                            err.getField(), err.getDefaultMessage())))
            .toList();
    String message =
        fieldErrors.stream()
            .map(err -> err.field() + ": " + err.message())
            .collect(Collectors.joining("; "));

    ErrorResponse body =
        new ErrorResponse(
            Instant.now(),
            400,
            "VALIDATION_ERROR",
            message.isBlank() ? "Validation failed" : message,
            request.getRequestURI(),
            requestId(request),
            fieldErrors);
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    // Don't leak internal details.
    ErrorResponse body =
        new ErrorResponse(
            Instant.now(),
            500,
            "INTERNAL_SERVER_ERROR",
            "Unexpected error",
            request.getRequestURI(),
            requestId(request),
            List.of());
    return ResponseEntity.internalServerError().body(body);
  }

  private String requestId(HttpServletRequest request) {
    Object attribute = request.getAttribute("requestId");
    return attribute == null ? request.getHeader("X-Request-Id") : attribute.toString();
  }
}
