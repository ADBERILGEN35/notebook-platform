package com.notebook.lumen.identity.sso.api;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.auth.application.AuthCookieService;
import com.notebook.lumen.identity.shared.config.AuthTransportProperties;
import com.notebook.lumen.identity.shared.exception.SsoException;
import com.notebook.lumen.identity.sso.application.SsoCallbackResult;
import com.notebook.lumen.identity.sso.application.SsoProviderView;
import com.notebook.lumen.identity.sso.application.SsoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/sso")
public class SsoController {
  private final SsoService ssoService;
  private final AuthCookieService authCookieService;
  private final AuthTransportProperties authTransportProperties;
  private final AuditService auditService;

  public SsoController(
      SsoService ssoService,
      AuthCookieService authCookieService,
      AuthTransportProperties authTransportProperties,
      AuditService auditService) {
    this.ssoService = ssoService;
    this.authCookieService = authCookieService;
    this.authTransportProperties = authTransportProperties;
    this.auditService = auditService;
  }

  @GetMapping("/providers")
  public SsoProvidersResponse providers() {
    return new SsoProvidersResponse(ssoService.listProviders());
  }

  @GetMapping("/{providerId}/authorize")
  public ResponseEntity<Void> authorize(
      @PathVariable String providerId,
      @RequestParam(required = false) String returnUrl,
      HttpServletRequest request) {
    URI location = ssoService.buildAuthorizeUri(providerId, returnUrl, request);
    return ResponseEntity.status(302).location(location).build();
  }

  @GetMapping("/{providerId}/callback")
  public ResponseEntity<AuthResponse> callback(
      @PathVariable String providerId,
      @RequestParam String code,
      @RequestParam String state,
      HttpServletRequest request,
      HttpServletResponse response) {
    SsoCallbackResult result;
    try {
      result = ssoService.handleCallback(providerId, code, state, request);
    } catch (SsoException ex) {
      auditService.record(
          "SSO_LOGIN_FAILED",
          null,
          "SSO",
          null,
          request,
          java.util.Map.of("provider", providerId, "errorCode", ex.getErrorCode()));
      HttpHeaders errorHeaders = new HttpHeaders();
      errorHeaders.setLocation(URI.create("/login?error=" + ex.getErrorCode()));
      return ResponseEntity.status(302).headers(errorHeaders).build();
    }
    auditService.record(
        "SSO_LOGIN_SUCCESS",
        result.authResponse().user().id(),
        "USER",
        result.authResponse().user().id(),
        request,
        java.util.Map.of("provider", providerId));
    if (authTransportProperties.cookieTransportEnabled()) {
      authCookieService.writeAuthCookies(response, request, result.authResponse());
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(result.returnUrl()));
    if (authTransportProperties.bearerTransportEnabled()) {
      return ResponseEntity.status(302).headers(headers).body(result.authResponse());
    }
    return ResponseEntity.status(302).headers(headers).build();
  }

  public record SsoProvidersResponse(List<SsoProviderView> providers) {}
}
