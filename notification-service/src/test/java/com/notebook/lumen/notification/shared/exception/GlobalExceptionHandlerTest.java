package com.notebook.lumen.notification.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceController;
import com.notebook.lumen.notification.preference.api.WorkspaceNotificationPreferenceDtos.WorkspaceNotificationPreferencePatchRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void preferenceMissingHeaderMapsToAccessDenied() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/notification-preferences");
    var response =
        handler.handleMissingHeader(new MissingRequestHeaderException("X-User-Id", null), request);
    assertThat(response.getStatusCode().value()).isEqualTo(403);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().errorCode()).isEqualTo("NOTIFICATION_PREFERENCE_ACCESS_DENIED");
  }

  @Test
  void workspacePreferenceMissingHeaderMapsToWorkspaceAccessDenied() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(
        "/notification-preferences/workspaces/00000000-0000-0000-0000-000000000001");
    var response =
        handler.handleMissingHeader(new MissingRequestHeaderException("X-User-Id", null), request);
    assertThat(response.getStatusCode().value()).isEqualTo(403);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().errorCode())
        .isEqualTo("WORKSPACE_NOTIFICATION_PREFERENCE_ACCESS_DENIED");
  }

  @Test
  void workspacePreferenceValidationMapsToInvalidWorkspaceRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(
        "/notification-preferences/workspaces/00000000-0000-0000-0000-000000000001");
    Method m =
        WorkspaceNotificationPreferenceController.class.getDeclaredMethod(
            "patch", String.class, UUID.class, WorkspaceNotificationPreferencePatchRequest.class);
    MethodParameter parameter = new MethodParameter(m, 2);
    WorkspaceNotificationPreferencePatchRequest body =
        new WorkspaceNotificationPreferencePatchRequest(Collections.emptyList());
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(body, "request");
    binding.addError(new FieldError("request", "updates", "must not be empty"));
    var ex = new MethodArgumentNotValidException(parameter, binding);
    var response = handler.handleValidation(ex, request);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().errorCode())
        .isEqualTo("INVALID_WORKSPACE_NOTIFICATION_PREFERENCE_REQUEST");
  }

  @Test
  void preferenceParseErrorMapsToInvalidRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/notification-preferences");
    var response =
        handler.handleRequestParseErrors(
            new HttpMessageNotReadableException("bad", new MockHttpInputMessage(new byte[0])),
            request);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().errorCode())
        .isEqualTo("INVALID_NOTIFICATION_DELIVERY_PREFERENCE_REQUEST");
  }
}
