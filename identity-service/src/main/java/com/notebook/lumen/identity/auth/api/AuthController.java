package com.notebook.lumen.identity.auth.api;

import com.notebook.lumen.identity.auth.application.AuthService;
import com.notebook.lumen.identity.auth.application.AuthCookieService;
import com.notebook.lumen.identity.shared.config.AuthTransportProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

  private final AuthService authService;
  private final AuthCookieService authCookieService;
  private final AuthTransportProperties authTransportProperties;

  public AuthController(
      AuthService authService,
      AuthCookieService authCookieService,
      AuthTransportProperties authTransportProperties) {
    this.authService = authService;
    this.authCookieService = authCookieService;
    this.authTransportProperties = authTransportProperties;
  }

  @Operation(
      summary = "Signup",
      description = "Create a new user account and return access/refresh tokens.")
  @PostMapping(
      path = "/signup",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> signup(
      @Valid @RequestBody SignupRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return withCookieIfNeeded(authService.signup(request, httpRequest), httpRequest, httpResponse);
  }

  @Operation(summary = "Login", description = "Authenticate user and return access/refresh tokens.")
  @PostMapping(
      path = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return withCookieIfNeeded(authService.login(request, httpRequest), httpRequest, httpResponse);
  }

  @Operation(summary = "Refresh", description = "Rotate refresh token and return new tokens.")
  @PostMapping(
      path = "/refresh",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> refresh(
      @RequestBody(required = false) RefreshTokenRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String refreshTokenCookie = authCookieService.readRefreshTokenCookie(httpRequest);
    return withCookieIfNeeded(
        authService.refresh(request, httpRequest, refreshTokenCookie), httpRequest, httpResponse);
  }

  @Operation(
      summary = "Logout",
      description = "Revoke one refresh token owned by the access token subject.")
  @PostMapping(path = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> logout(
      @RequestBody(required = false) LogoutRequest request,
      @AuthenticationPrincipal Jwt accessToken,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String refreshTokenCookie = authCookieService.readRefreshTokenCookie(httpRequest);
    authService.logout(request, accessToken, httpRequest, refreshTokenCookie);
    if (authTransportProperties.cookieTransportEnabled()) {
      authCookieService.clearAuthCookies(httpResponse);
    }
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Revoke all refresh tokens",
      description = "Revoke all active refresh tokens for the authenticated user.")
  @PostMapping(
      path = "/revoke-all",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public RevokeAllResponse revokeAll(
      @RequestBody(required = false) RevokeAllRequest request,
      @AuthenticationPrincipal Jwt accessToken,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    RevokeAllResponse response = authService.revokeAll(request, accessToken, httpRequest);
    if (authTransportProperties.cookieTransportEnabled()) {
      authCookieService.clearAuthCookies(httpResponse);
    }
    return response;
  }

  @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
  public AuthMeResponse me(@AuthenticationPrincipal Jwt accessToken) {
    return authService.me(accessToken);
  }

  private ResponseEntity<AuthResponse> withCookieIfNeeded(
      AuthResponse response, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    if (authTransportProperties.cookieTransportEnabled()) {
      if (!response.mfaRequired()) {
        authCookieService.writeAuthCookies(httpResponse, httpRequest, response);
      }
    }
    if (authTransportProperties.bearerTransportEnabled()) {
      return ResponseEntity.ok(response);
    }
    return ResponseEntity.ok(
        new AuthResponse(
            null,
            null,
            "Cookie",
            response.expiresIn(),
            response.user(),
            response.mfaRequired(),
            response.mfaSessionId(),
            response.availableMethods()));
  }
}
