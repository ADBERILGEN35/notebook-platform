package com.notebook.lumen.identity.breakglass;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/break-glass")
public class BreakGlassController {

  private final BreakGlassService breakGlassService;

  public BreakGlassController(BreakGlassService breakGlassService) {
    this.breakGlassService = breakGlassService;
  }

  @PostMapping(
      path = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassDtos.BreakGlassLoginResponse login(
      @Valid @RequestBody BreakGlassDtos.BreakGlassLoginRequest request,
      HttpServletRequest httpRequest) {
    // Never log token or reason.
    String token = request == null ? "" : request.token();
    String reason = request == null ? "" : request.reason();
    return breakGlassService.login(token, reason);
  }

  /**
   * Read-only readiness status. Safe to expose publicly: never includes token/hash.
   *
   * <p>Kept as a separate endpoint so runbooks/CLI can quickly check posture without admin access.
   */
  @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> status() {
    BreakGlassDtos.BreakGlassStatusResponse s = breakGlassService.status();
    return Map.of(
        "enabled",
        s.enabled(),
        "tokenConfigured",
        s.tokenConfigured(),
        "sessionTtlMinutes",
        s.sessionTtlMinutes(),
        "maxActiveSessions",
        s.maxActiveSessions(),
        "requireReason",
        s.requireReason(),
        "requireMfa",
        s.requireMfa());
  }
}

