package com.notebook.lumen.identity.scim.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class ScimAuthServiceTest {

  @Test
  void rejectsWhenDisabled() {
    ScimAuthService service =
        new ScimAuthService(
            new ScimProperties(false, "", "", true, "notebook-admins", true, 5, false, 100, 10),
            mock(AuditService.class));
    HttpServletRequest request = mock(HttpServletRequest.class);
    assertThatThrownBy(() -> service.requireAuthorized(request))
        .isInstanceOf(ScimException.class)
        .hasMessageContaining("disabled");
  }

  @Test
  void acceptsValidBearerToken() {
    ScimAuthService service =
        new ScimAuthService(
            new ScimProperties(
                true, "scim-secret", "", true, "notebook-admins", true, 5, false, 100, 10),
            mock(AuditService.class));
    HttpServletRequest request = mock(HttpServletRequest.class);
    org.mockito.Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer scim-secret");
    assertThatCode(() -> service.requireAuthorized(request)).doesNotThrowAnyException();
  }
}
