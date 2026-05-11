package com.notebook.lumen.identity.scim.application;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ScimAuthService {
  private final ScimProperties properties;
  private final AuditService auditService;

  public ScimAuthService(ScimProperties properties, AuditService auditService) {
    this.properties = properties;
    this.auditService = auditService;
  }

  public void requireAuthorized(HttpServletRequest request) {
    if (!properties.enabled()) {
      throw new ScimException(HttpStatus.NOT_FOUND, "invalidTarget", "SCIM is disabled");
    }
    if (!properties.authConfigured()) {
      throw new ScimException(HttpStatus.NOT_FOUND, "invalidTarget", "SCIM is disabled");
    }
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith("Bearer ")) {
      recordFailedAuth(request, "missing_bearer");
      throw new ScimException(HttpStatus.UNAUTHORIZED, "invalidToken", "SCIM_UNAUTHORIZED");
    }
    String token = header.substring("Bearer ".length()).trim();
    if (token.isBlank() || !tokenMatches(token)) {
      recordFailedAuth(request, "invalid_bearer");
      throw new ScimException(HttpStatus.UNAUTHORIZED, "invalidToken", "SCIM_UNAUTHORIZED");
    }
  }

  private boolean tokenMatches(String token) {
    if (properties.bearerTokenHash() != null && !properties.bearerTokenHash().isBlank()) {
      return sha256Hex(token).equalsIgnoreCase(properties.bearerTokenHash().trim());
    }
    return properties.bearerToken() != null && properties.bearerToken().equals(token);
  }

  private String sha256Hex(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Missing SHA-256 algorithm", ex);
    }
  }

  private void recordFailedAuth(HttpServletRequest request, String reason) {
    auditService.record("SCIM_AUTH_FAILED", null, "SCIM", null, request, Map.of("reason", reason));
  }
}
