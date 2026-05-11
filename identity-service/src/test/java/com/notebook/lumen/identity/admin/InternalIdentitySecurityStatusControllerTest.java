package com.notebook.lumen.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditAccessException;
import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InternalIdentitySecurityStatusControllerTest {

  @Test
  void whenDisabled_throwsNotFound() {
    var controller =
        new InternalIdentitySecurityStatusController(
            new InternalAdminStatusProperties(false),
            mock(AuditAdminAuthorizer.class),
            mock(IdentitySecurityStatusService.class));

    assertThatThrownBy(() -> controller.identitySecurity("Bearer x"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode().value())
                    .isEqualTo(HttpStatus.NOT_FOUND.value()));
  }

  @Test
  void whenEnabled_invokesAuthorizerWithStatusScope() {
    AuditAdminAuthorizer authorizer = mock(AuditAdminAuthorizer.class);
    IdentitySecurityStatusService service = mock(IdentitySecurityStatusService.class);
    var body =
        new IdentitySecurityStatusResponse(
            new IdentitySecurityStatusResponse.Sso(false, 0, false, false, false),
            new IdentitySecurityStatusResponse.Scim(false, false, false, false),
            new IdentitySecurityStatusResponse.Mfa(false, false),
            new IdentitySecurityStatusResponse.Siem(false, "noop", false, false, false),
            new IdentitySecurityStatusResponse.AdminRbac(false, true, Map.of()),
            new IdentitySecurityStatusResponse.BreakGlass(false, false, 15, 1, true, true),
            false,
            null);
    when(service.build()).thenReturn(body);

    var controller =
        new InternalIdentitySecurityStatusController(
            new InternalAdminStatusProperties(true), authorizer, service);

    IdentitySecurityStatusResponse result = controller.identitySecurity("Bearer tok");
    assertThat(result).isSameAs(body);
    verify(authorizer).authorize("Bearer tok", AuditAdminAuthorizer.ADMIN_STATUS_SCOPE);
  }

  @Test
  void whenAuthorizerRejects_propagates() {
    AuditAdminAuthorizer authorizer = mock(AuditAdminAuthorizer.class);
    doThrow(new AuditAccessException(HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_REQUIRED", "missing"))
        .when(authorizer)
        .authorize(null, AuditAdminAuthorizer.ADMIN_STATUS_SCOPE);

    var controller =
        new InternalIdentitySecurityStatusController(
            new InternalAdminStatusProperties(true),
            authorizer,
            mock(IdentitySecurityStatusService.class));

    assertThatThrownBy(() -> controller.identitySecurity(null))
        .isInstanceOf(AuditAccessException.class);
  }
}
