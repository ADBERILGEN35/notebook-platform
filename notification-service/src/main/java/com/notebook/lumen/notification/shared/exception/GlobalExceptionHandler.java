package com.notebook.lumen.notification.shared.exception;

import com.notebook.lumen.common.security.sanitization.SensitiveDataSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(NotificationException.class)
  ResponseEntity<ErrorResponse> handleNotification(
      NotificationException ex, HttpServletRequest request) {
    return ResponseEntity.status(ex.getStatus())
        .body(
            new ErrorResponse(
                Instant.now(),
                ex.getStatus().value(),
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI(),
                requestId(request),
                List.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/notification-preferences")) {
      return ResponseEntity.badRequest()
          .body(
              new ErrorResponse(
                  Instant.now(),
                  400,
                  "INVALID_NOTIFICATION_PREFERENCE_REQUEST",
                  "Invalid notification preference request",
                  request.getRequestURI(),
                  requestId(request),
                  List.of()));
    }
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

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  ResponseEntity<ErrorResponse> handleRequestParseErrors(Exception ex, HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/notification-preferences")) {
      return ResponseEntity.badRequest()
          .body(
              new ErrorResponse(
                  Instant.now(),
                  400,
                  "INVALID_NOTIFICATION_PREFERENCE_REQUEST",
                  "Invalid notification preference request",
                  request.getRequestURI(),
                  requestId(request),
                  List.of()));
    }
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "Invalid request",
                request.getRequestURI(),
                requestId(request),
                List.of()));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ErrorResponse> handleMissingHeader(
      MissingRequestHeaderException ex, HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/notification-preferences")) {
      return ResponseEntity.status(403)
          .body(
              new ErrorResponse(
                  Instant.now(),
                  403,
                  "NOTIFICATION_PREFERENCE_ACCESS_DENIED",
                  "Notification preference access denied",
                  request.getRequestURI(),
                  requestId(request),
                  List.of()));
    }
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "Missing required header",
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
                "INTERNAL_NOTIFICATION_ERROR",
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
