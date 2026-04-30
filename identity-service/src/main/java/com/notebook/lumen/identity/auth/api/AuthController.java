package com.notebook.lumen.identity.auth.api;

import com.notebook.lumen.identity.auth.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @Operation(
      summary = "Signup",
      description = "Create a new user account and return access/refresh tokens.")
  @PostMapping(
      path = "/signup",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AuthResponse signup(
      @Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
    return authService.signup(request, httpRequest);
  }

  @Operation(summary = "Login", description = "Authenticate user and return access/refresh tokens.")
  @PostMapping(
      path = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AuthResponse login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    return authService.login(request, httpRequest);
  }

  @Operation(summary = "Refresh", description = "Rotate refresh token and return new tokens.")
  @PostMapping(
      path = "/refresh",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AuthResponse refresh(
      @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
    return authService.refresh(request, httpRequest);
  }
}
