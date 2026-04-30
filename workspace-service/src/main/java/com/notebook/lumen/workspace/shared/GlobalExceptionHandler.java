package com.notebook.lumen.workspace.shared;

import com.notebook.lumen.common.security.sanitization.SensitiveDataSanitizer;
import com.notebook.lumen.workspace.shared.exception.WorkspaceRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(WorkspaceRuntimeException.class)
  ResponseEntity<ErrorResponse> handleWorkspace(
      WorkspaceRuntimeException ex, HttpServletRequest request) {
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
                error ->
                    new ErrorResponse.FieldError(
                        error.getField(),
                        SensitiveDataSanitizer.validationMessageFor(
                            error.getField(), error.getDefaultMessage())))
            .toList();
    String message =
        fieldErrors.stream()
            .map(error -> error.field() + ": " + error.message())
            .collect(Collectors.joining("; "));
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                message,
                request.getRequestURI(),
                requestId(request),
                fieldErrors));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ErrorResponse> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "Validation failed",
                request.getRequestURI(),
                requestId(request),
                List.of()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorResponse> handleIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    return ResponseEntity.status(409)
        .body(
            new ErrorResponse(
                Instant.now(),
                409,
                "DATA_INTEGRITY_VIOLATION",
                "Duplicate or invalid workspace data",
                request.getRequestURI(),
                requestId(request),
                List.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    return ResponseEntity.internalServerError()
        .body(
            new ErrorResponse(
                Instant.now(),
                500,
                "INTERNAL_WORKSPACE_ERROR",
                "Unexpected error",
                request.getRequestURI(),
                requestId(request),
                List.of()));
  }

  private String requestId(HttpServletRequest request) {
    Object attribute = request.getAttribute("requestId");
    return attribute == null ? request.getHeader("X-Request-Id") : attribute.toString();
  }
}
