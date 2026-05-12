package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.identity.shared.exception.IdentityRuntimeException;
import org.springframework.http.HttpStatus;

public final class BreakGlassException extends IdentityRuntimeException {
  public BreakGlassException(String errorCode, HttpStatus httpStatus, String message) {
    super(errorCode, httpStatus, message);
  }

  public static BreakGlassException disabled() {
    return new BreakGlassException(
        "BREAK_GLASS_DISABLED", HttpStatus.NOT_FOUND, "Break-glass auth is disabled");
  }

  public static BreakGlassException invalidToken() {
    return new BreakGlassException(
        "BREAK_GLASS_INVALID_TOKEN", HttpStatus.FORBIDDEN, "Invalid break-glass token");
  }

  public static BreakGlassException mfaRequired() {
    return new BreakGlassException(
        "BREAK_GLASS_MFA_REQUIRED", HttpStatus.FORBIDDEN, "Break-glass requires MFA");
  }

  public static BreakGlassException mfaInvalid() {
    return new BreakGlassException(
        "BREAK_GLASS_MFA_INVALID", HttpStatus.FORBIDDEN, "Break-glass MFA verification failed");
  }

  public static BreakGlassException rateLimited() {
    return new BreakGlassException(
        "BREAK_GLASS_RATE_LIMITED",
        HttpStatus.TOO_MANY_REQUESTS,
        "Break-glass attempts are rate limited");
  }

  public static BreakGlassException reasonRequired() {
    return new BreakGlassException(
        "BREAK_GLASS_REASON_REQUIRED",
        HttpStatus.BAD_REQUEST,
        "A reason (minimum 20 characters) is required");
  }

  public static BreakGlassException sessionLimitExceeded() {
    return new BreakGlassException(
        "BREAK_GLASS_SESSION_LIMIT_EXCEEDED",
        HttpStatus.TOO_MANY_REQUESTS,
        "Break-glass session limit exceeded");
  }

  public static BreakGlassException assertionReplayed() {
    return new BreakGlassException(
        "BREAK_GLASS_ASSERTION_REPLAYED",
        HttpStatus.FORBIDDEN,
        "Break-glass assertion replay detected");
  }

  public static BreakGlassException rotationNotFound() {
    return new BreakGlassException(
        "BREAK_GLASS_ROTATION_NOT_FOUND",
        HttpStatus.NOT_FOUND,
        "Break-glass token rotation event not found");
  }

  public static BreakGlassException rotationInvalidTransition(String reason) {
    return new BreakGlassException(
        "BREAK_GLASS_ROTATION_INVALID_TRANSITION",
        HttpStatus.CONFLICT,
        reason == null || reason.isBlank() ? "Invalid rotation state transition" : reason);
  }

  public static BreakGlassException rotationNotChanged() {
    return new BreakGlassException(
        "BREAK_GLASS_ROTATION_NOT_CHANGED",
        HttpStatus.CONFLICT,
        "Configured break-glass token hash has not changed");
  }

  public static BreakGlassException rotationApiDisabled() {
    return new BreakGlassException(
        "BREAK_GLASS_ROTATION_API_DISABLED",
        HttpStatus.FORBIDDEN,
        "Break-glass rotation API is disabled");
  }

  public static BreakGlassException rotationLimitExceeded() {
    return new BreakGlassException(
        "BREAK_GLASS_ROTATION_LIMIT_EXCEEDED",
        HttpStatus.TOO_MANY_REQUESTS,
        "Break-glass rotation open-event limit exceeded");
  }
}
