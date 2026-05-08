package com.notebook.lumen.notification.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingRequestHeaderException;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void preferenceMissingHeaderMapsToAccessDenied() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/notification-preferences");
    var response =
        handler.handleMissingHeader(
            new MissingRequestHeaderException("X-User-Id", null), request);
    assertThat(response.getStatusCode().value()).isEqualTo(403);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().errorCode()).isEqualTo("NOTIFICATION_PREFERENCE_ACCESS_DENIED");
  }

  @Test
  void preferenceParseErrorMapsToInvalidRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/notification-preferences");
    var response =
        handler.handleRequestParseErrors(
            new HttpMessageNotReadableException("bad", new MockHttpInputMessage(new byte[0])), request);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().errorCode()).isEqualTo("INVALID_NOTIFICATION_PREFERENCE_REQUEST");
  }
}
