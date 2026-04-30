package com.notebook.lumen.workspace.shared.exception;

import org.springframework.http.HttpStatus;

public final class Exceptions {
  private Exceptions() {}

  public static WorkspaceRuntimeException notFound(String code, String message) {
    return new SimpleWorkspaceException(HttpStatus.NOT_FOUND, code, message);
  }

  public static WorkspaceRuntimeException forbidden(String code, String message) {
    return new SimpleWorkspaceException(HttpStatus.FORBIDDEN, code, message);
  }

  public static WorkspaceRuntimeException conflict(String code, String message) {
    return new SimpleWorkspaceException(HttpStatus.CONFLICT, code, message);
  }

  public static WorkspaceRuntimeException badRequest(String code, String message) {
    return new SimpleWorkspaceException(HttpStatus.BAD_REQUEST, code, message);
  }

  public static WorkspaceRuntimeException unauthorized(String code, String message) {
    return new SimpleWorkspaceException(HttpStatus.UNAUTHORIZED, code, message);
  }

  private static final class SimpleWorkspaceException extends WorkspaceRuntimeException {
    private SimpleWorkspaceException(HttpStatus status, String code, String message) {
      super(status, code, message);
    }
  }
}
